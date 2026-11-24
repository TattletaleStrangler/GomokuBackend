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
 * Quick match and the open-games list (FEATURES.md, priority B1).
 *
 * <p>Quick match joins the oldest public room with the same rule and time control, or
 * opens one and waits; the lobby lists only public waiting rooms.
 */
class GameMatchmakingTest extends GameTestSupport {

    @Autowired
    private GameRepository repository;

    /** Rooms are not transactional across HTTP calls, so each test starts with a clean lobby. */
    @BeforeEach
    void cleanPublicRooms() {
        repository.deleteAll(repository.findByStatus(GameStatus.WAITING).stream()
                .filter(room -> room.getVisibility() == Visibility.PUBLIC)
                .toList());
    }

    @Test
    void quickMatchOpensAPublicRoomAndListsIt() throws Exception {
        Player host = newPlayer();
        Player guest = newPlayer();

        Response matched = quickMatch(host, "FREESTYLE", "RAPID");

        assertThat(matched.status()).isEqualTo(200);
        assertThat(matched.text("$.status")).isEqualTo("WAITING");
        assertThat(matched.text("$.code")).hasSize(6);

        Response lobby = lobby(guest);
        assertThat(lobby.status()).isEqualTo(200);
        assertThat(lobby.number("$.length()")).isEqualTo(1);
        assertThat(lobby.text("$[0].creatorNickname")).isEqualTo(host.nickname());
        assertThat(lobby.text("$[0].rule")).isEqualTo("FREESTYLE");
        assertThat(lobby.text("$[0].timeControl")).isEqualTo("RAPID");
        assertThat(lobby.text("$[0].code")).isEqualTo(matched.text("$.code"));
    }

    @Test
    void quickMatchPairsWithTheWaitingRoom() throws Exception {
        Player host = newPlayer();
        Player guest = newPlayer();
        String gameId = quickMatch(host, "RENJU", "BLITZ").text("$.gameId");

        Response matched = quickMatch(guest, "RENJU", "BLITZ");

        assertThat(matched.status()).isEqualTo(200);
        assertThat(matched.text("$.status")).isEqualTo("ACTIVE");
        assertThat(matched.text("$.gameId")).isEqualTo(gameId);
        // The room was opened as ANY and the guest asked for nothing: the guest is black.
        assertThat(matched.text("$.you")).isEqualTo("BLACK");
        assertThat(matched.text("$.turn")).isEqualTo("BLACK");

        Response hostView = snapshot(host, gameId);
        assertThat(hostView.text("$.you")).isEqualTo("WHITE");
        assertThat(lobby(guest).number("$.length()")).isZero();
    }

    @Test
    void quickMatchDoesNotPairDifferentSettings() throws Exception {
        Player host = newPlayer();
        Player guest = newPlayer();
        quickMatch(host, "FREESTYLE", "RAPID");

        Response other = quickMatch(guest, "FREESTYLE", "BLITZ");

        assertThat(other.text("$.status")).isEqualTo("WAITING");
        assertThat(lobby(guest).number("$.length()")).isEqualTo(2);
    }

    @Test
    void privateRoomsStayOutOfTheLobby() throws Exception {
        Player host = newPlayer();
        Player guest = newPlayer();

        Response created = createGame(host, "FREESTYLE", "UNTIMED");
        assertThat(created.status()).isEqualTo(201);

        assertThat(lobby(guest).number("$.length()")).isZero();
    }

    @Test
    void aPublicRoomNobodyJoinedExpires() throws Exception {
        Player host = newPlayer();
        String gameId = quickMatch(host, "FREESTYLE", "UNTIMED").text("$.gameId");

        advanceClock(Duration.ofMinutes(16));
        int expired = games.expireWaitingRooms();

        assertThat(expired).isEqualTo(1);
        assertThat(snapshot(host, gameId).text("$.status")).isEqualTo("ABANDONED");
        assertThat(lobby(host).number("$.length()")).isZero();
    }

    @Test
    void aPlayerCannotHoldTwoRooms() throws Exception {
        Player host = newPlayer();
        quickMatch(host, "FREESTYLE", "UNTIMED");

        Response second = quickMatch(host, "RENJU", "UNTIMED");

        assertThat(second.status()).isEqualTo(409);
        assertThat(second.text("$.code")).isEqualTo("ACTIVE_GAME_EXISTS");
    }
}