package com.oldmermaidman.gomoku.backend.user;

import com.oldmermaidman.gomoku.backend.game.GameRepository;
import com.oldmermaidman.gomoku.backend.game.domain.GameStatus;
import com.oldmermaidman.gomoku.backend.game.domain.Visibility;
import com.oldmermaidman.gomoku.backend.testsupport.GameTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The leaderboard (FEATURES.md, B2).
 *
 * <p>Assertions are relative to the players a test creates, because the board is
 * global: other test classes leave their own finished games in the same database.
 */
class LeaderboardTest extends GameTestSupport {

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
    void aRankedWinPutsTheWinnerAboveTheLoser() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        rate(alice, 2000);
        rate(bob, 1900);

        quickMatch(alice, "FREESTYLE", "RAPID");
        Response matched = quickMatch(bob, "FREESTYLE", "RAPID");
        assertThat(matched.text("$.status")).isEqualTo("ACTIVE");
        // The room was opened as ANY, so bob took black; black resigns and alice wins.
        action(bob, matched.text("$.gameId"), "resign");

        Response board = leaderboard(alice);
        int aliceRank = board.numbers(rankOf(alice)).get(0);
        int bobRank = board.numbers(rankOf(bob)).get(0);

        assertThat(aliceRank).isLessThan(bobRank);
        assertThat(board.numbers(propertyOf(alice, "wins"))).containsExactly(1);
        assertThat(board.numbers(propertyOf(alice, "losses"))).containsExactly(0);
        assertThat(board.numbers(propertyOf(bob, "losses"))).containsExactly(1);
        assertThat(board.numbers(propertyOf(alice, "rating")).get(0)).isGreaterThan(2000);
        assertThat(board.numbers(propertyOf(bob, "rating")).get(0)).isLessThan(1900);
    }

    @Test
    void aPlayerWithoutFinishedGamesIsNotListed() throws Exception {
        Player fresh = newPlayer();

        Response board = leaderboard(fresh);

        assertThat(board.size(of(fresh))).isZero();
    }

    @Test
    void ranksAreSequential() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        quickMatch(alice, "FREESTYLE", "UNTIMED");
        Response matched = quickMatch(bob, "FREESTYLE", "UNTIMED");
        action(bob, matched.text("$.gameId"), "resign");

        Response board = leaderboard(alice, 2);

        assertThat(board.number("$.length()")).isEqualTo(2);
        assertThat(board.number("$[0].rank")).isEqualTo(1);
        assertThat(board.number("$[1].rank")).isEqualTo(2);
    }

    @Test
    void aDeletedAccountLeavesTheLeaderboard() throws Exception {
        Player dave = newPlayer();
        Player erin = newPlayer();
        rate(dave, 2100);
        rate(erin, 2000);

        quickMatch(dave, "FREESTYLE", "UNTIMED");
        Response matched = quickMatch(erin, "FREESTYLE", "UNTIMED");
        action(erin, matched.text("$.gameId"), "resign");
        assertThat(leaderboard(erin).size(of(dave))).isEqualTo(1);

        Response deleted = delete(
                "/api/v1/auth/account", "{\"password\":\"password-123\"}", dave.token());

        assertThat(deleted.status()).isEqualTo(204);
        assertThat(leaderboard(erin).size(of(dave))).isZero();
    }

    private static String of(Player player) {
        return "$[?(@.nickname=='" + player.nickname() + "')]";
    }

    private static String rankOf(Player player) {
        return of(player) + ".rank";
    }

    private static String propertyOf(Player player, String name) {
        return of(player) + "." + name;
    }

    /** Sets a player's rating directly, so a test can decide who leads. */
    private void rate(Player player, int rating) {
        User user = users.findByNicknameIgnoreCase(player.nickname()).orElseThrow();
        user.setRating(rating);
        users.save(user);
    }
}