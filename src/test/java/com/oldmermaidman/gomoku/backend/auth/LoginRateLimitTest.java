package com.oldmermaidman.gomoku.backend.auth;

import com.oldmermaidman.gomoku.backend.testsupport.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The per-nickname limit on failed logins: guessing passwords is stopped after a few
 * attempts, even when the attacker switches addresses.
 */
@TestPropertySource(properties = {
        "app.rate-limit.per-ip=1000",
        "app.rate-limit.max-failed-logins=3"
})
class LoginRateLimitTest extends ApiTestSupport {

    @Test
    void wrongPasswordsBlockTheNicknameEvenWhenTheRightOneArrives() throws Exception {
        String nickname = nickname();
        assertThat(register(nickname, "password-123").status()).isEqualTo(201);

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(post("/api/v1/auth/login", credentials(nickname, "wrong-password"), null).status())
                    .as("attempt %s", attempt)
                    .isEqualTo(401);
        }

        Response blocked = post("/api/v1/auth/login", credentials(nickname, "password-123"), null);

        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.text("$.code")).isEqualTo("RATE_LIMITED");
    }

    @Test
    void aSuccessfulLoginClearsTheCounter() throws Exception {
        String nickname = nickname();
        assertThat(register(nickname, "password-123").status()).isEqualTo(201);

        assertThat(post("/api/v1/auth/login", credentials(nickname, "wrong-password"), null).status())
                .isEqualTo(401);
        assertThat(post("/api/v1/auth/login", credentials(nickname, "password-123"), null).status())
                .isEqualTo(200);

        // The counter was reset, so the nickname has its full budget again.
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(post("/api/v1/auth/login", credentials(nickname, "wrong-password"), null).status())
                    .as("attempt %s", attempt)
                    .isEqualTo(401);
        }
        assertThat(post("/api/v1/auth/login", credentials(nickname, "password-123"), null).status())
                .isEqualTo(429);
    }

    @Test
    void theLimitBelongsToTheNicknameNotTheAddress() throws Exception {
        String blocked = nickname();
        assertThat(register(blocked, "password-123").status()).isEqualTo(201);
        for (int attempt = 1; attempt <= 3; attempt++) {
            post("/api/v1/auth/login", credentials(blocked, "wrong-password"), null);
        }
        assertThat(post("/api/v1/auth/login", credentials(blocked, "password-123"), null).status())
                .isEqualTo(429);

        // Another account from the same address is unaffected.
        String other = nickname();
        assertThat(register(other, "password-123").status()).isEqualTo(201);
        assertThat(post("/api/v1/auth/login", credentials(other, "password-123"), null).status())
                .isEqualTo(200);
    }
}