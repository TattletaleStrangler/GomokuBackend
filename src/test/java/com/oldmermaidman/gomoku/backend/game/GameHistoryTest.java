package com.oldmermaidman.gomoku.backend.game;

import com.oldmermaidman.gomoku.backend.game.domain.GameStatus;
import com.oldmermaidman.gomoku.backend.game.domain.Visibility;
import com.oldmermaidman.gomoku.backend.testsupport.GameTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A player's game history and the replay behind it (FEATURES.md, B2).
 *
 * <p>The list is finished games only, newest first; the moves come from
 * {@code GET /games/{id}}, which the replay screen calls for each row.
 */
class GameHistoryTest extends GameTestSupport {

    @Autowired
    private GameRepository games;

    /** Rooms are not transactional across HTTP calls, so each test starts clean. */
    @BeforeEach
    void cleanPublicRooms() {
        games.deleteAll(games.findByStatus(GameStatus.WAITING).stream()
                .filter(room -> room.getVisibility() == Visibility.PUBLIC)
                .toList());
    }

    @Test
    void historyListsFinishedGamesNewestFirst() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();

        String first = openGame(alice, bob, "UNTIMED");
        assertThat(move(alice, first, 1, 7, 7).status()).isEqualTo(200);
        assertThat(action(bob, first, "resign").status()).isEqualTo(200);
        advanceClock(Duration.ofSeconds(1));

        String second = openGame(alice, bob, "UNTIMED");
        assertThat(action(alice, second, "resign").status()).isEqualTo(200);

        Response history = history(alice);
        assertThat(history.number("$.length()")).isEqualTo(2);
        assertThat(history.text("$[0].gameId")).isEqualTo(second);
        assertThat(history.text("$[1].gameId")).isEqualTo(first);
        assertThat(history.text("$[0].you")).isEqualTo("BLACK");
        assertThat(history.text("$[0].result")).isEqualTo("WHITE_WIN");
        assertThat(history.text("$[1].result")).isEqualTo("BLACK_WIN");
        assertThat(history.text("$[0].opponentNickname")).isEqualTo(bob.nickname());
        assertThat(history.number("$[1].moveCount")).isEqualTo(1);
        assertThat(history.number("$[0].moveCount")).isZero();
    }

    @Test
    void theReplayReadsTheMovesOfAFinishedGame() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");
        move(alice, gameId, 1, 7, 7);
        move(bob, gameId, 2, 8, 8);
        action(alice, gameId, "resign");

        Response replay = snapshot(bob, gameId);

        assertThat(replay.text("$.status")).isEqualTo("FINISHED");
        assertThat(replay.number("$.moves.length()")).isEqualTo(2);
        assertThat(replay.text("$.moves[1].color")).isEqualTo("WHITE");
    }

    @Test
    void aRunningGameAndAnAbandonedRoomAreNotHistory() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();

        openGame(alice, bob, "UNTIMED");

        Player carol = newPlayer();
        String room = createGame(carol, "FREESTYLE", "UNTIMED").text("$.gameId");
        assertThat(action(carol, room, "leave").status()).isEqualTo(200);

        assertThat(history(alice).number("$.length()")).isZero();
        assertThat(history(carol).number("$.length()")).isZero();
    }

    @Test
    void aFinishedGameIsVisibleOnlyToItsPlayers() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        Player carol = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");
        action(alice, gameId, "resign");

        assertThat(history(bob).number("$.length()")).isEqualTo(1);
        assertThat(history(carol).number("$.length()")).isZero();
    }

    @Test
    void historyKeepsTheColourAndTheRankedDelta() throws Exception {
        Player host = newPlayer();
        Player guest = newPlayer();
        quickMatch(host, "FREESTYLE", "RAPID");
        Response matched = quickMatch(guest, "FREESTYLE", "RAPID");
        // The guest took black in the ANY room; black resigns, white (host) wins.
        action(guest, matched.text("$.gameId"), "resign");

        Response history = history(host);

        assertThat(history.number("$.length()")).isEqualTo(1);
        assertThat(history.text("$[0].you")).isEqualTo("WHITE");
        assertThat(history.flag("$[0].ranked")).isTrue();
        assertThat(history.number("$[0].ratingDelta")).isEqualTo(16);
        assertThat(history.text("$[0].opponentNickname")).isEqualTo(guest.nickname());
        assertThat(history.number("$[0].opponentRating")).isEqualTo(1184);
    }

    @Test
    void historyHonoursTheLimit() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String first = openGame(alice, bob, "UNTIMED");
        action(alice, first, "resign");

        assertThat(history(alice, 1).number("$.length()")).isEqualTo(1);
        assertThat(history(alice, 0).number("$.length()")).isEqualTo(1);
    }
}