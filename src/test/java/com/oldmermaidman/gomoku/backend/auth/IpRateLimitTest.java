package com.oldmermaidman.gomoku.backend.auth;

import com.oldmermaidman.gomoku.backend.testsupport.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The per-address limit on {@code /api/v1/auth/*}.
 *
 * <p>The limit is set to three so the test needs exactly four calls; the nickname
 * limit is raised out of the way, because this test is about the address.
 */
@TestPropertySource(properties = {
        "app.rate-limit.per-ip=3",
        "app.rate-limit.max-failed-logins=1000"
})
class IpRateLimitTest extends ApiTestSupport {

    @Test
    void theAddressLimitStopsAFlood() throws Exception {
        String nickname = nickname();
        assertThat(register(nickname, "password-123").status()).isEqualTo(201);
        assertThat(post("/api/v1/auth/login", credentials(nickname, "wrong-password"), null).status())
                .isEqualTo(401);
        assertThat(post("/api/v1/auth/login", credentials(nickname, "wrong-password"), null).status())
                .isEqualTo(401);

        // Fourth call from the same address inside the window.
        Response blocked = post("/api/v1/auth/login", credentials(nickname, "password-123"), null);

        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.text("$.code")).isEqualTo("RATE_LIMITED");
    }

    @Test
    void otherEndpointsAreNotAffectedByTheAuthLimit() throws Exception {
        // Spend the whole auth budget first.
        register(nickname(), "password-123");
        post("/api/v1/auth/login", credentials(nickname(), "password-123"), null);
        post("/api/v1/auth/login", credentials(nickname(), "password-123"), null);
        assertThat(post("/api/v1/auth/login", credentials(nickname(), "password-123"), null).status())
                .isEqualTo(429);

        // A protected endpoint still answers — it is not part of the auth budget.
        assertThat(get("/api/v1/me", null).status()).isEqualTo(401);
    }
}