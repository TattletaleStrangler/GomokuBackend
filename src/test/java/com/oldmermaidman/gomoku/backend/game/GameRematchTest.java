package com.oldmermaidman.gomoku.backend.game;

import com.oldmermaidman.gomoku.backend.game.domain.GameStatus;
import com.oldmermaidman.gomoku.backend.game.domain.Visibility;
import com.oldmermaidman.gomoku.backend.testsupport.GameTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rematch of a finished game (FEATURES.md).
 *
 * <p>The first caller opens a private room with the colours swapped; the finished game
 * points at it so the opponent is offered the same room, and their call joins it.
 */
class GameRematchTest extends GameTestSupport {

    @Autowired
    private GameRepository games;

    @BeforeEach
    void cleanPublicRooms() {
        games.deleteAll(games.findByStatus(GameStatus.WAITING).stream()
                .filter(room -> room.getVisibility() == Visibility.PUBLIC)
                .toList());
    }

    @Test
    void theOfferCreatesARoomWithSwappedColours() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");
        action(bob, gameId, "resign");

        Response offer = rematch(alice, gameId);

        assertThat(offer.status()).isEqualTo(200);
        assertThat(offer.text("$.status")).isEqualTo("WAITING");
        // While the room waits the creator still sits in the black seat; the chosen
        // colour is applied when the opponent joins, so the swap shows up below.
        String rematchId = offer.text("$.gameId");
        assertThat(rematchId).isNotEqualTo(gameId);
        assertThat(snapshot(bob, gameId).text("$.rematchGameId")).isEqualTo(rematchId);

        Response accepted = rematch(bob, gameId);

        assertThat(accepted.text("$.status")).isEqualTo("ACTIVE");
        assertThat(accepted.text("$.gameId")).isEqualTo(rematchId);
        assertThat(accepted.text("$.you")).isEqualTo("BLACK");
        assertThat(accepted.text("$.black.nickname")).isEqualTo(bob.nickname());
        assertThat(accepted.text("$.white.nickname")).isEqualTo(alice.nickname());
        assertThat(accepted.text("$.turn")).isEqualTo("BLACK");
        assertThat(snapshot(alice, rematchId).text("$.you")).isEqualTo("WHITE");
    }

    @Test
    void askingAgainReturnsTheSameRoom() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");
        action(alice, gameId, "resign");

        String first = rematch(bob, gameId).text("$.gameId");
        Response again = rematch(bob, gameId);

        assertThat(again.text("$.status")).isEqualTo("WAITING");
        assertThat(again.text("$.gameId")).isEqualTo(first);
    }

    @Test
    void aRunningGameCannotBeRematched() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");

        Response refused = rematch(alice, gameId);

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.text("$.code")).isEqualTo("REMATCH_NOT_ALLOWED");
    }

    @Test
    void onlyThePlayersMayRematch() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        Player carol = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");
        action(alice, gameId, "resign");

        Response refused = rematch(carol, gameId);

        assertThat(refused.status()).isEqualTo(403);
        assertThat(refused.text("$.code")).isEqualTo("NOT_A_PARTICIPANT");
    }

    @Test
    void aRankedGameKeepsTheRematchRankedAndACasualOneStaysCasual() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String casual = openGame(alice, bob, "UNTIMED");
        action(alice, casual, "resign");

        assertThat(rematch(bob, casual).flag("$.ranked")).isFalse();

        Player carol = newPlayer();
        Player dave = newPlayer();
        quickMatch(carol, "FREESTYLE", "RAPID");
        Response ranked = quickMatch(dave, "FREESTYLE", "RAPID");
        action(dave, ranked.text("$.gameId"), "resign");

        assertThat(rematch(carol, ranked.text("$.gameId")).flag("$.ranked")).isTrue();
    }

    @Test
    void aRematchRoomIsPrivateAndNotInTheLobby() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");
        action(alice, gameId, "resign");

        rematch(bob, gameId);

        assertThat(lobby(alice).number("$.length()")).isZero();
    }
}