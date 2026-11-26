package com.oldmermaidman.gomoku.backend.common;

import com.oldmermaidman.gomoku.backend.telemetry.TelemetryService;
import com.oldmermaidman.gomoku.backend.testsupport.GameTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What happens when a client asks for something that does not exist.
 *
 * <p>An outdated app (or anyone guessing addresses) used to get 500 and a row in
 * {@code error_events} for a path that simply is not there — the log of a run against a
 * stale server was full of `Invalid UUID string: lobby`. These are client mistakes and
 * must stay out of the server-error table.
 */
class ClientMistakeTest extends GameTestSupport {

    @Autowired
    private TelemetryService telemetry;

    @Test
    void anAddressShapedLikeAnIdButNotAUuidIsNotFound() throws Exception {
        Player player = newPlayer();

        Response response = get("/api/v1/games/not-a-uuid", player.token());

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.text("$.code")).isEqualTo("NOT_FOUND");
        assertThat(recorded("/api/v1/games/not-a-uuid")).isFalse();
    }

    @Test
    void theWrongVerbIsRejectedAsSuch() throws Exception {
        Player player = newPlayer();

        Response response = post("/api/v1/leaderboard", player.token());

        assertThat(response.status()).isEqualTo(405);
        assertThat(response.text("$.code")).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(recorded("/api/v1/leaderboard")).isFalse();
    }

    @Test
    void anUnmappedAddressIsNotFound() throws Exception {
        Player player = newPlayer();

        Response response = get("/api/v1/nothing-here", player.token());

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.text("$.code")).isEqualTo("NOT_FOUND");
        assertThat(recorded("/api/v1/nothing-here")).isFalse();
    }

    /** True when the address ended up in the server-error table. */
    private boolean recorded(String path) {
        return telemetry.recentErrors().stream().anyMatch(error -> path.equals(error.getPath()));
    }
}