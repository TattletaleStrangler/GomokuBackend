package com.oldmermaidman.gomoku.backend.auth;

import com.oldmermaidman.gomoku.backend.common.ApiError;
import com.oldmermaidman.gomoku.backend.common.ApiException;
import com.oldmermaidman.gomoku.backend.config.JwtProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Access tokens: round trip, expiry, tampering and a weak secret. */
class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private static JwtService service(Duration accessTtl) {
        return new JwtService(new JwtProperties(SECRET, accessTtl, Duration.ofDays(30)));
    }

    private final JwtService jwt = service(Duration.ofMinutes(15));

    @Test
    void aTokenCarriesTheUser() {
        UUID id = UUID.randomUUID();

        AuthenticatedUser user = jwt.verify(jwt.issueAccessToken(id, "ann"));

        assertEquals(id, user.id());
        assertEquals("ann", user.nickname());
    }

    @Test
    void anExpiredTokenIsReportedAsExpired() {
        String token = service(Duration.ofSeconds(-60)).issueAccessToken(UUID.randomUUID(), "ann");

        ApiException error = assertThrows(ApiException.class, () -> jwt.verify(token));

        assertEquals(ApiError.TOKEN_EXPIRED, error.error());
    }

    @Test
    void aBrokenTokenIsRejected() {
        ApiException error = assertThrows(ApiException.class, () -> jwt.verify("not-a-token"));

        assertEquals(ApiError.TOKEN_INVALID, error.error());
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() {
        JwtService other = new JwtService(
                new JwtProperties("another-secret-another-secret-123456", Duration.ofMinutes(15), Duration.ofDays(30)));
        String foreignToken = other.issueAccessToken(UUID.randomUUID(), "mallory");

        ApiException error = assertThrows(ApiException.class, () -> jwt.verify(foreignToken));

        assertEquals(ApiError.TOKEN_INVALID, error.error());
    }

    @Test
    void aShortSecretStopsTheApplication() {
        JwtProperties weak = new JwtProperties("too-short", Duration.ofMinutes(15), Duration.ofDays(30));

        assertThrows(IllegalStateException.class, () -> new JwtService(weak));
    }

    @Test
    void theAccessLifetimeIsReportedInSeconds() {
        assertEquals(900, service(Duration.ofMinutes(15)).accessTtlSeconds());
    }
}