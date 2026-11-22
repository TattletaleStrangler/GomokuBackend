package com.oldmermaidman.gomoku.backend.auth;

import com.oldmermaidman.gomoku.backend.testsupport.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole account lifecycle over HTTP: register, login, profile, refresh rotation,
 * logout and deletion.
 *
 * <p>The rate limits are raised here on purpose — this class makes many calls from one
 * address, and the limits themselves are covered by {@link IpRateLimitTest} and
 * {@link LoginRateLimitTest}.
 */
@TestPropertySource(properties = {
        "app.rate-limit.per-ip=1000",
        "app.rate-limit.max-failed-logins=1000"
})
class AuthApiTest extends ApiTestSupport {

    @Test
    void registrationReturnsTokensAndZeroedStats() throws Exception {
        Response registered = register(nickname(), "password-123");

        assertThat(registered.status()).isEqualTo(201);
        String accessToken = registered.text("$.accessToken");
        assertThat(accessToken).isNotBlank();
        assertThat(registered.text("$.refreshToken")).isNotBlank();
        assertThat(registered.number("$.expiresIn")).isEqualTo(900);

        Response me = get("/api/v1/me", accessToken);
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.text("$.nickname")).isNotBlank();
        assertThat(me.number("$.stats.wins")).isZero();
        assertThat(me.number("$.stats.losses")).isZero();
        assertThat(me.number("$.stats.draws")).isZero();
    }

    @Test
    void aNicknameIsUniqueRegardlessOfCase() throws Exception {
        String nickname = nickname();
        register(nickname, "password-123");

        Response duplicate = register(nickname.toUpperCase(), "password-123");

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.text("$.code")).isEqualTo("NICKNAME_TAKEN");
    }

    @Test
    void aShortPasswordIsRejectedBeforeAnythingHappens() throws Exception {
        Response response = register(nickname(), "short");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("$.code")).isEqualTo("VALIDATION_FAILED");
        assertThat(response.text("$.details.password")).isNotBlank();
    }

    @Test
    void loginAcceptsTheRightPasswordOnly() throws Exception {
        String nickname = nickname();
        register(nickname, "password-123");

        assertThat(post("/api/v1/auth/login", credentials(nickname, "password-123"), null).status())
                .isEqualTo(200);

        Response wrong = post("/api/v1/auth/login", credentials(nickname, "wrong-password"), null);
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(wrong.text("$.code")).isEqualTo("BAD_CREDENTIALS");
    }

    @Test
    void anUnknownAccountGivesTheSameAnswerAsAWrongPassword() throws Exception {
        Response response = post("/api/v1/auth/login", credentials(nickname(), "password-123"), null);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.text("$.code")).isEqualTo("BAD_CREDENTIALS");
    }

    @Test
    void theProfileNeedsAToken() throws Exception {
        Response anonymous = get("/api/v1/me", null);
        assertThat(anonymous.status()).isEqualTo(401);
        assertThat(anonymous.text("$.code")).isEqualTo("UNAUTHORIZED");

        Response broken = get("/api/v1/me", "not-a-token");
        assertThat(broken.status()).isEqualTo(401);
        assertThat(broken.text("$.code")).isEqualTo("TOKEN_INVALID");
    }

    @Test
    void refreshRotatesTheTokenAndReuseEndsTheSession() throws Exception {
        Response registered = register(nickname(), "password-123");
        String refreshToken = registered.text("$.refreshToken");

        Response refreshed = post("/api/v1/auth/refresh", refreshBody(refreshToken), null);
        assertThat(refreshed.status()).isEqualTo(200);
        String nextRefresh = refreshed.text("$.refreshToken");
        assertThat(nextRefresh).isNotEqualTo(refreshToken);

        // The rotated token is revoked: presenting it again is treated as theft and
        // ends every session of that user, including the freshly issued one.
        Response replayed = post("/api/v1/auth/refresh", refreshBody(refreshToken), null);
        assertThat(replayed.status()).isEqualTo(401);
        assertThat(replayed.text("$.code")).isEqualTo("TOKEN_INVALID");

        assertThat(post("/api/v1/auth/refresh", refreshBody(nextRefresh), null).status()).isEqualTo(401);
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        Response registered = register(nickname(), "password-123");
        String accessToken = registered.text("$.accessToken");
        String refreshToken = registered.text("$.refreshToken");

        Response logout = post("/api/v1/auth/logout", refreshBody(refreshToken), accessToken);
        assertThat(logout.status()).isEqualTo(204);

        assertThat(post("/api/v1/auth/refresh", refreshBody(refreshToken), null).status()).isEqualTo(401);
    }

    @Test
    void deletingAnAccountAnonymisesItAndBlocksLogin() throws Exception {
        String nickname = nickname();
        Response registered = register(nickname, "password-123");
        String accessToken = registered.text("$.accessToken");

        Response deleted = delete("/api/v1/auth/account", "{\"password\":\"password-123\"}", accessToken);
        assertThat(deleted.status()).isEqualTo(204);

        Response login = post("/api/v1/auth/login", credentials(nickname, "password-123"), null);
        assertThat(login.status()).isEqualTo(401);
        assertThat(login.text("$.code")).isEqualTo("BAD_CREDENTIALS");

        // The access token is still cryptographically valid, but the account is gone.
        assertThat(get("/api/v1/me", accessToken).status()).isEqualTo(401);

        // The nickname is free again, because the old row was anonymised.
        assertThat(register(nickname, "password-456").status()).isEqualTo(201);
    }

    @Test
    void aWrongPasswordDoesNotDeleteTheAccount() throws Exception {
        Response registered = register(nickname(), "password-123");
        String accessToken = registered.text("$.accessToken");

        Response deleted = delete("/api/v1/auth/account", "{\"password\":\"not-my-password\"}", accessToken);
        assertThat(deleted.status()).isEqualTo(401);
        assertThat(deleted.text("$.code")).isEqualTo("BAD_CREDENTIALS");

        assertThat(get("/api/v1/me", accessToken).status()).isEqualTo(200);
    }
}