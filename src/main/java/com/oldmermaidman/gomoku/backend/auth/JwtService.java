package com.oldmermaidman.gomoku.backend.auth;

import com.oldmermaidman.gomoku.backend.common.ApiError;
import com.oldmermaidman.gomoku.backend.common.ApiException;
import com.oldmermaidman.gomoku.backend.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies access tokens (HS256).
 *
 * <p>Access tokens are stateless and short-lived; the long-lived part of a session is
 * the refresh token, which lives in the database and can be revoked (see
 * {@link AuthService}).
 */
@Service
public class JwtService {

    /** HS256 needs at least 256 bits of key material. */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        byte[] secret = properties.secret() == null
                ? new byte[0]
                : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET (app.jwt.secret) must be at least " + MIN_SECRET_BYTES
                            + " bytes, got " + secret.length + ". See Backend/.env.example.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.properties = properties;
    }

    public String issueAccessToken(UUID userId, String nickname) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("nickname", nickname)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTtl())))
                .signWith(key)
                .compact();
    }

    /** Lifetime of an access token in seconds, as the client needs it. */
    public long accessTtlSeconds() {
        return properties.accessTtl().toSeconds();
    }

    /**
     * The user behind a token.
     *
     * @throws ApiException {@code TOKEN_EXPIRED} or {@code TOKEN_INVALID} — the client
     *                      distinguishes them: the first one triggers a refresh
     */
    public AuthenticatedUser verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get("nickname", String.class));
        } catch (ExpiredJwtException e) {
            throw new ApiException(ApiError.TOKEN_EXPIRED, "Access token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(ApiError.TOKEN_INVALID, "Access token is not valid");
        }
    }
}