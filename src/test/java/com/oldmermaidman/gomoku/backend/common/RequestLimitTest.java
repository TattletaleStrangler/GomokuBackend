package com.oldmermaidman.gomoku.backend.common;

import com.oldmermaidman.gomoku.backend.auth.AuthRateLimitFilter;
import com.oldmermaidman.gomoku.backend.config.RateLimitProperties;
import com.oldmermaidman.gomoku.backend.game.GameRepository;
import com.oldmermaidman.gomoku.backend.game.domain.GameStatus;
import com.oldmermaidman.gomoku.backend.game.domain.Visibility;
import com.oldmermaidman.gomoku.backend.testsupport.GameTestSupport;
import com.oldmermaidman.gomoku.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The limits added by the security audit, P1: per-player game calls, per-address crash
 * reports, the body size cap and the admin form lockout.
 */
class RequestLimitTest extends GameTestSupport {

    @Autowired
    private RateLimiter limiter;

    @Autowired
    private RateLimitProperties properties;

    @Autowired
    private UserRepository users;

    @Autowired
    private GameRepository gameRepository;

    @BeforeEach
    void cleanPublicRooms() {
        gameRepository.deleteAll(gameRepository.findByStatus(GameStatus.WAITING).stream()
                .filter(room -> room.getVisibility() == Visibility.PUBLIC)
                .toList());
        // The limiter is one instance for the whole test JVM: start each test from zero.
        limiter.clearAll();
    }

    @Test
    void joiningByCodeIsLimitedPerPlayer() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        createGame(alice, "FREESTYLE", "UNTIMED");

        // A code that does not exist is refused by the game (404) but still counted.
        for (int i = 0; i < 10; i++) {
            assertThat(joinGame(bob, "AAAAAA").status()).isNotEqualTo(429);
        }

        Response refused = joinGame(bob, "AAAAAA");
        assertThat(refused.status()).isEqualTo(429);
        assertThat(refused.text("$.code")).isEqualTo("RATE_LIMITED");
    }

    @Test
    void movesAreLimitedPerPlayer() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");

        // A wrong move number is refused by the game (409), not by the limiter.
        for (int i = 0; i < 30; i++) {
            assertThat(move(alice, gameId, 999, 7, 7).status()).isNotEqualTo(429);
        }

        assertThat(move(alice, gameId, 999, 7, 7).status()).isEqualTo(429);
    }

    @Test
    void crashReportsAreLimitedPerAddress() throws Exception {
        String body = "{\"stacktrace\":\"java.lang.RuntimeException: x\"}";

        for (int i = 0; i < 20; i++) {
            assertThat(post("/api/v1/crash-reports", body, null).status()).isEqualTo(202);
        }

        Response refused = post("/api/v1/crash-reports", body, null);
        assertThat(refused.status()).isEqualTo(429);
        assertThat(refused.text("$.code")).isEqualTo("RATE_LIMITED");
    }

    @Test
    void aBodyLargerThanTheCapIsRefused() throws Exception {
        String huge = "{\"stacktrace\":\"" + "x".repeat(70_000) + "\"}";

        Response refused = post("/api/v1/crash-reports", huge, null);

        assertThat(refused.status()).isEqualTo(413);
        assertThat(refused.text("$.code")).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    void theAdminFormLocksOutAfterRepeatedFailures() throws Exception {
        for (int i = 0; i < properties.maxFailedLogins(); i++) {
            limiter.tryAcquire(
                    AuthRateLimitFilter.loginKey("owner"),
                    properties.maxFailedLogins(),
                    properties.failedLoginWindow());
        }

        // The limiter sits in front of the security chain, so the form is refused
        // before it even reaches CSRF or the password check.
        Response locked = postForm("/admin/login", "username=owner&password=guess", null);

        assertThat(locked.status()).isEqualTo(302);
        assertThat(locked.header("Location")).endsWith("/admin/login?locked");
    }

    private UUID idOf(Player player) {
        return users.findByNicknameIgnoreCase(player.nickname()).orElseThrow().getId();
    }
}