package com.oldmermaidman.gomoku.backend.auth;

import com.oldmermaidman.gomoku.backend.auth.dto.CredentialsRequest;
import com.oldmermaidman.gomoku.backend.auth.dto.ProfileResponse;
import com.oldmermaidman.gomoku.backend.auth.dto.RefreshTokenRequest;
import com.oldmermaidman.gomoku.backend.auth.dto.StatsResponse;
import com.oldmermaidman.gomoku.backend.auth.dto.TokenResponse;
import com.oldmermaidman.gomoku.backend.auth.dto.UserResponse;
import com.oldmermaidman.gomoku.backend.common.ApiError;
import com.oldmermaidman.gomoku.backend.common.ApiException;
import com.oldmermaidman.gomoku.backend.moderation.BanRepository;
import com.oldmermaidman.gomoku.backend.common.RateLimiter;
import com.oldmermaidman.gomoku.backend.config.JwtProperties;
import com.oldmermaidman.gomoku.backend.config.RateLimitProperties;
import com.oldmermaidman.gomoku.backend.user.User;
import com.oldmermaidman.gomoku.backend.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Registration, login and session handling.
 *
 * <p>Sessions are pairs of tokens: a short-lived access token (stateless JWT) and a
 * long-lived refresh token whose hash is stored here. Refreshing rotates the token —
 * the old one is revoked and linked to the new one — so a stolen token is usable at
 * most once, and a replayed one ends every session of that user.
 */
@Service
public class AuthService {

    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final String NICKNAME_TAKEN = "Nickname is already taken";
    private static final String BAD_CREDENTIALS = "Wrong nickname or password";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final BanRepository bans;
    private final SecureRandom random = new SecureRandom();

    /**
     * Hash checked when the nickname is unknown, so an unknown account costs the same
     * time as a wrong password and does not reveal which nicknames exist.
     */
    private final String dummyHash;

    /**
     * A revocation that must survive the failure it reports. The theft response
     * below ends the whole token chain and then rejects the request — in one
     * transaction the rejection would roll the revocation back, so it runs in its own.
     */
    private final TransactionTemplate revocationTransaction;

    public AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            RateLimiter rateLimiter,
            RateLimitProperties rateLimitProperties,
            BanRepository bans,
            PlatformTransactionManager transactionManager) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
        this.bans = bans;
        this.dummyHash = passwordEncoder.encode("gomoku-dummy-password");

        this.revocationTransaction = new TransactionTemplate(transactionManager);
        this.revocationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public TokenResponse register(CredentialsRequest request) {
        String nickname = request.nickname().trim();
        if (users.existsByNicknameIgnoreCase(nickname)) {
            throw new ApiException(ApiError.NICKNAME_TAKEN, NICKNAME_TAKEN);
        }

        User user = new User(
                UUID.randomUUID(),
                nickname,
                passwordEncoder.encode(request.password()),
                Instant.now());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two registrations with the same nickname met at the unique index.
            throw new ApiException(ApiError.NICKNAME_TAKEN, NICKNAME_TAKEN);
        }
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(CredentialsRequest request) {
        String nickname = request.nickname().trim();
        String guardKey = "login:" + nickname.toLowerCase(Locale.ROOT);

        // Guessing is stopped before any hashing: five wrong answers block the
        // nickname for a while (TZ_BACKEND.md §9).
        if (rateLimiter.isExceeded(
                guardKey, rateLimitProperties.maxFailedLogins(), rateLimitProperties.failedLoginWindow())) {
            throw new ApiException(ApiError.RATE_LIMITED, "Too many failed logins, try again later");
        }

        User user = users.findByNicknameIgnoreCase(nickname).orElse(null);

        // Same answer and about the same time for an unknown nickname, a deleted
        // account and a wrong password: the client must not learn which accounts exist.
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (user == null || user.isDeleted() || !passwordMatches) {
            rateLimiter.tryAcquire(
                    guardKey, rateLimitProperties.maxFailedLogins(), rateLimitProperties.failedLoginWindow());
            throw new ApiException(ApiError.BAD_CREDENTIALS, BAD_CREDENTIALS);
        }

        rateLimiter.reset(guardKey);
        // A ban that closes the account is checked here; a ban from ranked play is not,
        // because such a player may still sign in and play rooms by code (B4).
        if (bans.isAccountBanned(user.getId(), Instant.now())) {
            throw new ApiException(ApiError.BANNED, "This account is closed by an administrator");
        }
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokens.findByTokenHash(hash(request.refreshToken()))
                .orElseThrow(() -> new ApiException(ApiError.TOKEN_INVALID, "Refresh token is not valid"));
        Instant now = Instant.now();

        if (stored.isRevoked()) {
            // A rotated token came back: assume it was stolen and end every session.
            // The revocation is committed even though the request is then rejected.
            revocationTransaction.executeWithoutResult(
                    status -> refreshTokens.revokeAllForUser(stored.getUserId(), now));
            throw new ApiException(ApiError.TOKEN_INVALID, "Refresh token has already been used");
        }
        if (stored.isExpired(now)) {
            throw new ApiException(ApiError.TOKEN_EXPIRED, "Refresh token has expired");
        }

        User user = users.findByIdAndDeletedAtIsNull(stored.getUserId())
                .orElseThrow(() -> new ApiException(ApiError.TOKEN_INVALID, "Refresh token is not valid"));

        IssuedRefreshToken next = newRefreshToken(user.getId(), now);
        stored.revoke(next.entity().getId(), now);
        refreshTokens.save(next.entity());

        return new TokenResponse(
                jwtService.issueAccessToken(user.getId(), user.getNickname()),
                next.rawValue(),
                jwtService.accessTtlSeconds(),
                toResponse(user));
    }

    /** Idempotent: logging out of an unknown or already used token is not an error. */
    @Transactional
    public void logout(UUID userId, String rawRefreshToken) {
        refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .filter(token -> token.getUserId().equals(userId) && !token.isRevoked())
                .ifPresent(token -> token.revoke(null, Instant.now()));
    }

    @Transactional
    public void deleteAccount(UUID userId, String password) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(ApiError.UNAUTHORIZED, "Unknown account"));
        if (user.isDeleted()) {
            throw new ApiException(ApiError.ACCOUNT_DELETED, "Account is already deleted");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(ApiError.BAD_CREDENTIALS, "Wrong password");
        }

        user.anonymise(Instant.now());
        // Flush before the bulk update below: the bulk statement bypasses the entity
        // and must not leave the anonymisation pending in the persistence context.
        users.flush();
        refreshTokens.revokeAllForUser(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public ProfileResponse profile(UUID userId) {
        User user = users.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(ApiError.UNAUTHORIZED, "Unknown account"));
        UserRepository.StatsRow stats = users.statsFor(userId);

        return new ProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getRating(),
                user.getCreatedAt(),
                new StatsResponse(stats.getWins(), stats.getLosses(), stats.getDraws()));
    }

    // ---- tokens -----------------------------------------------------------

    private TokenResponse issueTokens(User user) {
        Instant now = Instant.now();
        IssuedRefreshToken refresh = newRefreshToken(user.getId(), now);
        refreshTokens.save(refresh.entity());

        return new TokenResponse(
                jwtService.issueAccessToken(user.getId(), user.getNickname()),
                refresh.rawValue(),
                jwtService.accessTtlSeconds(),
                toResponse(user));
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getNickname(), user.getCreatedAt());
    }

    /** A refresh token as stored, plus the value handed to the client exactly once. */
    private record IssuedRefreshToken(RefreshToken entity, String rawValue) {
    }

    private IssuedRefreshToken newRefreshToken(UUID userId, Instant now) {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawValue = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken entity = new RefreshToken(
                UUID.randomUUID(),
                userId,
                hash(rawValue),
                now,
                now.plus(jwtProperties.refreshTtl()));
        return new IssuedRefreshToken(entity, rawValue);
    }

    /** Only the hash is stored; the database never holds a usable token. */
    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM", e);
        }
    }
}