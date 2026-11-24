package com.oldmermaidman.gomoku.backend.testsupport;

import com.oldmermaidman.gomoku.backend.game.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base for game tests: a clock the test can move forward, the timeout sweeper switched
 * off (tests drive expiries themselves) and the calls every game test needs.
 */
@TestPropertySource(properties = {
        "app.rate-limit.per-ip=1000",
        "app.rate-limit.max-failed-logins=1000",
        "app.game.timeout-sweeper-enabled=false"
})
@Import(GameTestSupport.TestClock.class)
public abstract class GameTestSupport extends ApiTestSupport {

    /** A clock the test can move forward, so timeouts need no waiting. */
    public static class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        public void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    @TestConfiguration
    static class TestClock {

        private final MutableClock clock = new MutableClock();

        /** A different bean name than the application's {@code clock}: overriding is disabled. */
        @Bean
        @Primary
        MutableClock mutableClock() {
            return clock;
        }
    }

    @Autowired
    protected GameService games;

    @Autowired
    private MutableClock clock;

    protected void advanceClock(Duration duration) {
        clock.advance(duration);
    }

    protected record Player(String token, String nickname) {
    }

    protected Player newPlayer() throws Exception {
        String nickname = nickname();
        Response registered = register(nickname, "password-123");
        assertThat(registered.status()).isEqualTo(201);
        return new Player(registered.text("$.accessToken"), nickname);
    }

    protected Response createGame(Player player, String rule, String timeControl) throws Exception {
        return createGame(player, rule, timeControl, null);
    }

    /** [color] is BLACK / WHITE / ANY, or null to let the server use its default. */
    protected Response createGame(Player player, String rule, String timeControl, String color)
            throws Exception {
        String body = "{\"rule\":\"" + rule + "\",\"timeControl\":\"" + timeControl + "\""
                + (color == null ? "" : ",\"color\":\"" + color + "\"") + "}";
        return post("/api/v1/games", body, player.token());
    }

    protected Response joinGame(Player player, String code) throws Exception {
        return joinGame(player, code, null);
    }

    /** [color] is the joiner's wish; the room honours it only when it left the colour open. */
    protected Response joinGame(Player player, String code, String color) throws Exception {
        String body = "{\"code\":\"" + code + "\""
                + (color == null ? "" : ",\"color\":\"" + color + "\"") + "}";
        return post("/api/v1/games/join", body, player.token());
    }

    protected Response quickMatch(Player player, String rule, String timeControl) throws Exception {
        return quickMatch(player, rule, timeControl, null);
    }

    /** [color] is optional; null means "no preference" and opens the room as ANY. */
    protected Response quickMatch(Player player, String rule, String timeControl, String color)
            throws Exception {
        String body = "{\"rule\":\"" + rule + "\",\"timeControl\":\"" + timeControl + "\""
                + (color == null ? "" : ",\"color\":\"" + color + "\"") + "}";
        return post("/api/v1/games/quick", body, player.token());
    }

    protected Response lobby(Player player) throws Exception {
        return get("/api/v1/games/lobby", player.token());
    }

    protected Response history(Player player) throws Exception {
        return history(player, 20);
    }

    protected Response history(Player player, int limit) throws Exception {
        return get("/api/v1/games/history?limit=" + limit, player.token());
    }

    protected Response leaderboard(Player player) throws Exception {
        return leaderboard(player, 50);
    }

    protected Response leaderboard(Player player, int limit) throws Exception {
        return get("/api/v1/leaderboard?limit=" + limit, player.token());
    }

    /** Creates a room and has the opponent join it; returns the game id. */
    protected String openGame(Player creator, Player opponent, String timeControl) throws Exception {
        Response created = createGame(creator, "FREESTYLE", timeControl);
        assertThat(created.status()).isEqualTo(201);
        Response joined = joinGame(opponent, created.text("$.code"));
        assertThat(joined.status()).isEqualTo(200);
        return created.text("$.gameId");
    }

    protected Response move(Player player, String gameId, int ply, int x, int y) throws Exception {
        return post("/api/v1/games/" + gameId + "/moves",
                "{\"ply\":" + ply + ",\"x\":" + x + ",\"y\":" + y + "}", player.token());
    }

    protected Response action(Player player, String gameId, String name) throws Exception {
        return post("/api/v1/games/" + gameId + "/" + name, player.token());
    }

    protected Response undoRequest(Player player, String gameId) throws Exception {
        return action(player, gameId, "undo-request");
    }

    protected Response undoDecision(Player player, String gameId, boolean accept) throws Exception {
        return post("/api/v1/games/" + gameId + "/undo-decision",
                "{\"accept\":" + accept + "}", player.token());
    }

    protected Response snapshot(Player player, String gameId) throws Exception {
        return get("/api/v1/games/" + gameId, player.token());
    }
}