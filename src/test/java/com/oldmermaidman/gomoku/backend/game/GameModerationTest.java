package com.oldmermaidman.gomoku.backend.game;

import com.oldmermaidman.gomoku.backend.game.domain.GameStatus;
import com.oldmermaidman.gomoku.backend.game.domain.Visibility;
import com.oldmermaidman.gomoku.backend.moderation.BlockRepository;
import com.oldmermaidman.gomoku.backend.moderation.ReportRepository;
import com.oldmermaidman.gomoku.backend.testsupport.GameTestSupport;
import com.oldmermaidman.gomoku.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Blocking and reporting an opponent (FUTURE.md).
 *
 * <p>A block hides the pair from the lobby and from quick match in both directions;
 * a report is stored with the reason and the game, and is capped per day.
 */
class GameModerationTest extends GameTestSupport {

    @Autowired
    private GameRepository games;

    @Autowired
    private BlockRepository blocks;

    @Autowired
    private ReportRepository reports;

    @Autowired
    private UserRepository users;

    @BeforeEach
    void cleanPublicRooms() {
        games.deleteAll(games.findByStatus(GameStatus.WAITING).stream()
                .filter(room -> room.getVisibility() == Visibility.PUBLIC)
                .toList());
    }

    @Test
    void aBlockHidesThePairFromTheLobbyAndFromQuickMatch() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");
        action(alice, gameId, "resign");

        assertThat(block(alice, gameId).status()).isEqualTo(204);

        // Bob opens a public room: Alice must not see it...
        String bobRoom = quickMatch(bob, "FREESTYLE", "RAPID").text("$.gameId");
        assertThat(lobby(alice).size("$[?(@.creatorNickname=='" + bob.nickname() + "')]")).isZero();

        // ...and quick match must not put them together: Alice opens her own room.
        Response aliceMatch = quickMatch(alice, "FREESTYLE", "RAPID");
        assertThat(aliceMatch.text("$.status")).isEqualTo("WAITING");
        assertThat(aliceMatch.text("$.gameId")).isNotEqualTo(bobRoom);

        // The block is mutual for pairing: Bob does not see Alice's room either.
        assertThat(lobby(bob).size("$[?(@.creatorNickname=='" + alice.nickname() + "')]")).isZero();
    }

    @Test
    void blockingTwiceStoresOnePair() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");
        action(alice, gameId, "resign");

        assertThat(block(alice, gameId).status()).isEqualTo(204);
        assertThat(block(alice, gameId).status()).isEqualTo(204);

        var aliceId = users.findByNicknameIgnoreCase(alice.nickname()).orElseThrow().getId();
        var bobId = users.findByNicknameIgnoreCase(bob.nickname()).orElseThrow().getId();
        assertThat(blocks.existsByBlockerIdAndBlockedId(aliceId, bobId)).isTrue();
        assertThat(blocks.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void aReportKeepsTheReasonAndTheGame() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");
        action(alice, gameId, "resign");

        assertThat(report(bob, gameId, "CHEATING").status()).isEqualTo(204);

        var bobId = users.findByNicknameIgnoreCase(bob.nickname()).orElseThrow().getId();
        var stored = reports.findAll().stream()
                .filter(row -> row.getReporterId().equals(bobId))
                .toList();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getReason().name()).isEqualTo("CHEATING");
        assertThat(stored.get(0).getGameId().toString()).isEqualTo(gameId);
    }

    @Test
    void thereIsNobodyToModerateInAWaitingRoom() throws Exception {
        Player alice = newPlayer();
        String room = createGame(alice, "FREESTYLE", "UNTIMED").text("$.gameId");

        Response refused = block(alice, room);

        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.text("$.code")).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void aStrangerCannotModerate() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        Player carol = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");
        action(alice, gameId, "resign");

        assertThat(block(carol, gameId).status()).isEqualTo(403);
        assertThat(report(carol, gameId, "OTHER").status()).isEqualTo(403);
    }

    @Test
    void theSameGameCannotBeReportedTwice() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");
        action(alice, gameId, "resign");

        assertThat(report(bob, gameId, "OTHER").status()).isEqualTo(204);
        // The second complaint about the same game is accepted and ignored.
        assertThat(report(bob, gameId, "CHEATING").status()).isEqualTo(204);

        long stored = reports.findAll().stream()
                .filter(row -> row.getGameId() != null && row.getGameId().toString().equals(gameId))
                .count();
        assertThat(stored).isEqualTo(1);
    }

    @Test
    void reportsAreCappedPerDay() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();

        // One complaint per game, so the cap needs twenty games.
        for (int i = 0; i < 20; i++) {
            String gameId = openGame(bob, alice, "UNTIMED");
            action(bob, gameId, "resign");
            assertThat(report(bob, gameId, "OTHER").status()).isEqualTo(204);
        }

        String extra = openGame(bob, alice, "UNTIMED");
        action(bob, extra, "resign");
        Response refused = report(bob, extra, "OTHER");
        assertThat(refused.status()).isEqualTo(429);
        assertThat(refused.text("$.code")).isEqualTo("RATE_LIMITED");
    }
}