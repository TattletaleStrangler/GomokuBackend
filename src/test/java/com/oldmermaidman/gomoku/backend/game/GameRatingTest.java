package com.oldmermaidman.gomoku.backend.game;

import com.oldmermaidman.gomoku.backend.game.domain.GameStatus;
import com.oldmermaidman.gomoku.backend.game.domain.Visibility;
import com.oldmermaidman.gomoku.backend.testsupport.GameTestSupport;
import com.oldmermaidman.gomoku.backend.user.User;
import com.oldmermaidman.gomoku.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rating of quick-play games (FEATURES.md, priority B2).
 *
 * <p>Only public ranked rooms settle Elo; a room by code stays casual. The matchmaking
 * band starts narrow and widens while a room waits.
 */
class GameRatingTest extends GameTestSupport {

    @Autowired
    private GameRepository games;

    @Autowired
    private UserRepository users;

    @BeforeEach
    void cleanPublicRooms() {
        games.deleteAll(games.findByStatus(GameStatus.WAITING).stream()
                .filter(room -> room.getVisibility() == Visibility.PUBLIC)
                .toList());
    }

    @Test
    void aRankedGameSettlesBothRatings() throws Exception {
        Player host = newPlayer();
        Player guest = newPlayer();
        String gameId = quickMatch(host, "FREESTYLE", "RAPID").text("$.gameId");
        // The room was opened as ANY, so the guest takes black and the host white.
        Response matched = quickMatch(guest, "FREESTYLE", "RAPID");
        assertThat(matched.text("$.status")).isEqualTo("ACTIVE");
        assertThat(matched.flag("$.ranked")).isTrue();

        // Black resigns: white (the host) wins.
        assertThat(action(guest, gameId, "resign").status()).isEqualTo(200);

        Response view = snapshot(host, gameId);
        assertThat(view.number("$.white.ratingDelta")).isEqualTo(16);
        assertThat(view.number("$.black.ratingDelta")).isEqualTo(-16);
        assertThat(view.number("$.white.rating")).isEqualTo(1216);
        assertThat(view.number("$.black.rating")).isEqualTo(1184);

        assertThat(get("/api/v1/me", host.token()).number("$.rating")).isEqualTo(1216);
        assertThat(get("/api/v1/me", guest.token()).number("$.rating")).isEqualTo(1184);
    }

    @Test
    void aRoomByCodeLeavesRatingsAlone() throws Exception {
        Player host = newPlayer();
        Player guest = newPlayer();
        String gameId = openGame(host, guest, "UNTIMED");

        assertThat(action(host, gameId, "resign").status()).isEqualTo(200);

        Response view = snapshot(host, gameId);
        assertThat(view.flag("$.ranked")).isFalse();
        assertThat(view.has("$.black.ratingDelta")).isFalse();
        assertThat(get("/api/v1/me", host.token()).number("$.rating")).isEqualTo(1200);
        assertThat(get("/api/v1/me", guest.token()).number("$.rating")).isEqualTo(1200);
    }

    @Test
    void theBandKeepsFarApartPlayersWaitingAndWidensOverTime() throws Exception {
        Player farAhead = newPlayer();
        Player beginner = newPlayer();
        rate(farAhead, 1500);

        String waitingId = quickMatch(farAhead, "FREESTYLE", "UNTIMED").text("$.gameId");
        assertThat(snapshot(farAhead, waitingId).text("$.status")).isEqualTo("WAITING");

        // 300 apart against a 200 band: no match, the beginner waits in their own room.
        Response noMatch = quickMatch(beginner, "FREESTYLE", "UNTIMED");
        assertThat(noMatch.text("$.status")).isEqualTo("WAITING");
        assertThat(noMatch.text("$.gameId")).isNotEqualTo(waitingId);

        // Free the beginner, let the room age, and the band has grown enough.
        assertThat(action(beginner, noMatch.text("$.gameId"), "leave").status()).isEqualTo(200);
        advanceClock(Duration.ofSeconds(15));

        Response matched = quickMatch(beginner, "FREESTYLE", "UNTIMED");
        assertThat(matched.text("$.status")).isEqualTo("ACTIVE");
        assertThat(matched.text("$.gameId")).isEqualTo(waitingId);
    }

    @Test
    void matchmakingIgnoresPrivateRooms() throws Exception {
        Player host = newPlayer();
        Player guest = newPlayer();
        createGame(host, "FREESTYLE", "UNTIMED");

        Response matched = quickMatch(guest, "FREESTYLE", "UNTIMED");

        assertThat(matched.text("$.status")).isEqualTo("WAITING");
    }

    /** Sets a player's rating directly, so a test can put the two far apart. */
    private void rate(Player player, int rating) {
        User user = users.findByNicknameIgnoreCase(player.nickname()).orElseThrow();
        user.setRating(rating);
        users.save(user);
    }
}