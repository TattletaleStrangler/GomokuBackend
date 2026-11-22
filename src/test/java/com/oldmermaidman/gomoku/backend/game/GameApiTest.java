package com.oldmermaidman.gomoku.backend.game;

import com.oldmermaidman.gomoku.backend.testsupport.ApiTestSupport;
import org.junit.jupiter.api.Test;
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
 * Rooms and games over HTTP, against a real PostgreSQL.
 *
 * <p>Time is taken from a mutable clock, so a timeout can be tested without waiting
 * for it, and the background sweeper is switched off to keep the test deterministic —
 * the timeout test calls the service directly.
 */
@TestPropertySource(properties = {
        "app.rate-limit.per-ip=1000",
        "app.rate-limit.max-failed-logins=1000",
        "app.game.timeout-sweeper-enabled=false"
})
@Import(GameApiTest.TestClock.class)
class GameApiTest extends ApiTestSupport {

    /** A clock the test can move forward. */
    static class MutableClock extends Clock {

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

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    @TestConfiguration
    static class TestClock {

        private final MutableClock clock = new MutableClock();

        /**
         * A different bean name than the application's {@code clock}: overriding a
         * bean definition is disabled, and {@code @Primary} is what picks this one.
         */
        @Bean
        @Primary
        MutableClock mutableClock() {
            return clock;
        }
    }

    @Autowired
    private GameService games;

    @Autowired
    private MutableClock clock;

    private record Player(String token, String nickname) {
    }

    private Player newPlayer() throws Exception {
        String nickname = nickname();
        Response registered = register(nickname, "password-123");
        assertThat(registered.status()).isEqualTo(201);
        return new Player(registered.text("$.accessToken"), nickname);
    }

    private Response createGame(Player player, String rule, String timeControl) throws Exception {
        return post("/api/v1/games",
                "{\"rule\":\"" + rule + "\",\"timeControl\":\"" + timeControl + "\"}", player.token());
    }

    private Response joinGame(Player player, String code) throws Exception {
        return post("/api/v1/games/join", "{\"code\":\"" + code + "\"}", player.token());
    }

    private Response move(Player player, String gameId, int ply, int x, int y) throws Exception {
        return post("/api/v1/games/" + gameId + "/moves",
                "{\"ply\":" + ply + ",\"x\":" + x + ",\"y\":" + y + "}", player.token());
    }

    private Response action(Player player, String gameId, String name) throws Exception {
        return post("/api/v1/games/" + gameId + "/" + name, player.token());
    }

    private Response snapshot(Player player, String gameId) throws Exception {
        return get("/api/v1/games/" + gameId, player.token());
    }

    // ---- rooms ------------------------------------------------------------

    @Test
    void aRoomStartsWhenTheOpponentJoins() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();

        Response created = createGame(black, "RENJU", "RAPID");

        assertThat(created.status()).isEqualTo(201);
        String code = created.text("$.code");
        assertThat(code).hasSize(6);
        assertThat(created.text("$.status")).isEqualTo("WAITING");
        assertThat(created.text("$.you")).isEqualTo("BLACK");
        assertThat(created.has("$.turn")).isFalse();
        assertThat(created.has("$.white")).isFalse();
        assertThat(created.number("$.clocks.blackRemainingMs")).isEqualTo(15 * 60_000);

        Response joined = joinGame(white, code);

        assertThat(joined.status()).isEqualTo(200);
        assertThat(joined.text("$.status")).isEqualTo("ACTIVE");
        assertThat(joined.text("$.you")).isEqualTo("WHITE");
        assertThat(joined.text("$.turn")).isEqualTo("BLACK");
        assertThat(joined.number("$.ply")).isEqualTo(1);
        assertThat(joined.text("$.black.nickname")).isEqualTo(black.nickname());
        assertThat(joined.text("$.white.nickname")).isEqualTo(white.nickname());
    }

    @Test
    void aPlayerCannotJoinTheirOwnRoom() throws Exception {
        Player creator = newPlayer();
        String code = createGame(creator, "FREESTYLE", "UNTIMED").text("$.code");

        Response joined = joinGame(creator, code);

        assertThat(joined.status()).isEqualTo(409);
        assertThat(joined.text("$.code")).isEqualTo("SELF_JOIN");
    }

    @Test
    void anUnknownCodeIsReportedAsSuch() throws Exception {
        Response joined = joinGame(newPlayer(), "AAAAAA");

        assertThat(joined.status()).isEqualTo(404);
        assertThat(joined.text("$.code")).isEqualTo("ROOM_NOT_FOUND");
    }

    @Test
    void aFullRoomCannotBeJoinedAgain() throws Exception {
        Player creator = newPlayer();
        String code = createGame(creator, "FREESTYLE", "RAPID").text("$.code");
        assertThat(joinGame(newPlayer(), code).status()).isEqualTo(200);

        Response third = joinGame(newPlayer(), code);

        assertThat(third.status()).isEqualTo(409);
        assertThat(third.text("$.code")).isEqualTo("ROOM_FULL");
    }

    @Test
    void aPlayerCannotHaveTwoOpenGames() throws Exception {
        Player player = newPlayer();
        String firstGameId = createGame(player, "FREESTYLE", "RAPID").text("$.gameId");

        Response second = createGame(player, "FREESTYLE", "BLITZ");

        assertThat(second.status()).isEqualTo(409);
        assertThat(second.text("$.code")).isEqualTo("ACTIVE_GAME_EXISTS");
        assertThat(second.text("$.details.gameId")).isEqualTo(firstGameId);
    }

    @Test
    void leavingAWaitingRoomClosesItAndFreesThePlayer() throws Exception {
        Player creator = newPlayer();
        String gameId = createGame(creator, "FREESTYLE", "UNTIMED").text("$.gameId");

        Response left = action(creator, gameId, "leave");

        assertThat(left.status()).isEqualTo(200);
        assertThat(left.text("$.status")).isEqualTo("ABANDONED");
        assertThat(createGame(creator, "FREESTYLE", "UNTIMED").status()).isEqualTo(201);
    }

    // ---- moves ------------------------------------------------------------

    @Test
    void onlyThePlayerToMoveMayMoveAndOnlyWithTheRightNumber() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();
        Response created = createGame(black, "FREESTYLE", "RAPID");
        String gameId = created.text("$.gameId");
        joinGame(white, created.text("$.code"));

        Response outOfTurn = move(white, gameId, 1, 7, 7);
        assertThat(outOfTurn.status()).isEqualTo(409);
        assertThat(outOfTurn.text("$.code")).isEqualTo("NOT_YOUR_TURN");

        Response wrongPly = move(black, gameId, 5, 7, 7);
        assertThat(wrongPly.status()).isEqualTo(409);
        assertThat(wrongPly.text("$.code")).isEqualTo("WRONG_PLY");
        assertThat(wrongPly.number("$.details.expectedPly")).isEqualTo(1);

        Response played = move(black, gameId, 1, 7, 7);
        assertThat(played.status()).isEqualTo(200);
        assertThat(played.number("$.ply")).isEqualTo(2);
        assertThat(played.text("$.turn")).isEqualTo("WHITE");
        assertThat(played.text("$.moves[0].color")).isEqualTo("BLACK");

        Response occupied = move(white, gameId, 2, 7, 7);
        assertThat(occupied.status()).isEqualTo(409);
        assertThat(occupied.text("$.code")).isEqualTo("CELL_OCCUPIED");
    }

    @Test
    void fiveInARowFinishesTheGame() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();
        Response created = createGame(black, "FREESTYLE", "RAPID");
        String gameId = created.text("$.gameId");
        joinGame(white, created.text("$.code"));

        int[][] blackStones = {{3, 7}, {4, 7}, {5, 7}, {6, 7}};
        int[][] whiteStones = {{0, 0}, {2, 2}, {4, 4}, {6, 6}};
        for (int i = 0; i < whiteStones.length; i++) {
            assertThat(move(black, gameId, 2 * i + 1, blackStones[i][0], blackStones[i][1]).status())
                    .isEqualTo(200);
            assertThat(move(white, gameId, 2 * i + 2, whiteStones[i][0], whiteStones[i][1]).status())
                    .isEqualTo(200);
        }

        Response winning = move(black, gameId, 9, 7, 7);

        assertThat(winning.status()).isEqualTo(200);
        assertThat(winning.text("$.status")).isEqualTo("FINISHED");
        assertThat(winning.text("$.result")).isEqualTo("BLACK_WIN");
        assertThat(winning.text("$.finishReason")).isEqualTo("FIVE");
        assertThat(winning.has("$.turn")).isFalse();

        Response tooLate = move(white, gameId, 10, 10, 10);
        assertThat(tooLate.status()).isEqualTo(409);
        assertThat(tooLate.text("$.code")).isEqualTo("GAME_FINISHED");
    }

    @Test
    void resigningGivesTheGameToTheOpponent() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();
        Response created = createGame(black, "FREESTYLE", "RAPID");
        String gameId = created.text("$.gameId");
        joinGame(white, created.text("$.code"));

        Response resigned = action(white, gameId, "resign");

        assertThat(resigned.status()).isEqualTo(200);
        assertThat(resigned.text("$.status")).isEqualTo("FINISHED");
        assertThat(resigned.text("$.result")).isEqualTo("BLACK_WIN");
        assertThat(resigned.text("$.finishReason")).isEqualTo("RESIGN");
    }

    @Test
    void aFallenFlagEndsTheGame() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();
        Response created = createGame(black, "FREESTYLE", "BLITZ");
        String gameId = created.text("$.gameId");
        joinGame(white, created.text("$.code"));

        // Black is to move with three minutes; move the clock past them.
        clock.advance(Duration.ofMinutes(4));
        games.expireFlagged();

        Response after = snapshot(black, gameId);

        assertThat(after.text("$.status")).isEqualTo("FINISHED");
        assertThat(after.text("$.result")).isEqualTo("WHITE_WIN");
        assertThat(after.text("$.finishReason")).isEqualTo("TIMEOUT");
        assertThat(after.number("$.clocks.blackRemainingMs")).isZero();
    }

    @Test
    void aStrangerCannotSeeTheGame() throws Exception {
        Player black = newPlayer();
        Response created = createGame(black, "FREESTYLE", "RAPID");
        String gameId = created.text("$.gameId");

        Response peeked = snapshot(newPlayer(), gameId);

        assertThat(peeked.status()).isEqualTo(403);
        assertThat(peeked.text("$.code")).isEqualTo("NOT_A_PARTICIPANT");
    }
}