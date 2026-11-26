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
 * Draw offers (FEATURES.md).
 *
 * <p>The same request/answer shape as an undo: the opponent has ten seconds, a refusal
 * or a timeout costs the offerer an attempt, and an accepted offer ends the game with a
 * draw that settles the rating like any other result.
 */
class GameDrawTest extends GameTestSupport {

    @Autowired
    private UserRepository users;

    /** Named apart from the inherited {@code games} service, which the tests also use. */
    @Autowired
    private GameRepository gameRepository;

    @BeforeEach
    void cleanPublicRooms() {
        gameRepository.deleteAll(gameRepository.findByStatus(GameStatus.WAITING).stream()
                .filter(room -> room.getVisibility() == Visibility.PUBLIC)
                .toList());
    }

    @Test
    void agreeingToADrawEndsTheGameWithADraw() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String gameId = openGame(alice, bob, "RAPID");

        Response offer = drawOffer(alice, gameId);

        assertThat(offer.status()).isEqualTo(200);
        assertThat(offer.text("$.draw.requestedBy")).isEqualTo("BLACK");
        assertThat(offer.number("$.drawRemaining")).isEqualTo(2);

        Response agreed = drawDecision(bob, gameId, true);

        assertThat(agreed.text("$.status")).isEqualTo("FINISHED");
        assertThat(agreed.text("$.result")).isEqualTo("DRAW");
        assertThat(agreed.text("$.finishReason")).isEqualTo("AGREE");
        assertThat(agreed.has("$.draw")).isFalse();
    }

    @Test
    void refusingKeepsTheGameRunning() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");
        drawOffer(alice, gameId);

        Response refused = drawDecision(bob, gameId, false);

        assertThat(refused.text("$.status")).isEqualTo("ACTIVE");
        assertThat(refused.has("$.draw")).isFalse();
        // The attempt is spent for the offerer, not for the player who answered.
        assertThat(snapshot(alice, gameId).number("$.drawRemaining")).isEqualTo(2);
        assertThat(snapshot(bob, gameId).number("$.drawRemaining")).isEqualTo(3);
    }

    @Test
    void anOfferNobodyAnswersExpires() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");
        drawOffer(alice, gameId);

        advanceClock(Duration.ofSeconds(11));
        int expired = games.expireDrawRequests();

        // Other games in the shared database may also have had an offer waiting.
        assertThat(expired).isGreaterThanOrEqualTo(1);
        assertThat(snapshot(alice, gameId).has("$.draw")).isFalse();
        assertThat(snapshot(alice, gameId).text("$.status")).isEqualTo("ACTIVE");
        // The game goes on and the offerer may try again.
        assertThat(drawOffer(alice, gameId).status()).isEqualTo(200);
    }

    @Test
    void theLimitIsPerPlayer() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");

        for (int i = 0; i < 3; i++) {
            assertThat(drawOffer(alice, gameId).status()).isEqualTo(200);
            assertThat(drawDecision(bob, gameId, false).status()).isEqualTo(200);
        }

        Response refused = drawOffer(alice, gameId);
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.text("$.code")).isEqualTo("DRAW_LIMIT_REACHED");
    }

    @Test
    void onlyTheOpponentDecides() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        Player carol = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");
        drawOffer(alice, gameId);

        Response own = drawDecision(alice, gameId, true);
        assertThat(own.status()).isEqualTo(409);
        assertThat(own.text("$.code")).isEqualTo("DRAW_NOT_REQUESTED");

        assertThat(drawDecision(carol, gameId, true).status()).isEqualTo(403);
        // Leave no pending offer behind: other tests share this database.
        assertThat(drawDecision(bob, gameId, false).status()).isEqualTo(200);
    }

    @Test
    void anUndoAndADrawCannotBothWait() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String gameId = openGame(alice, bob, "UNTIMED");

        // Alice offers a draw first: her undo request has to wait.
        drawOffer(alice, gameId);
        Response undo = undoRequest(alice, gameId);
        assertThat(undo.status()).isEqualTo(409);
        assertThat(undo.text("$.code")).isEqualTo("REQUEST_ALREADY_PENDING");
        drawDecision(bob, gameId, false);

        // Now the other way round: a pending undo blocks a draw offer. Alice has just
        // moved, so it is her move that the undo would take back.
        move(alice, gameId, 1, 7, 7);
        assertThat(undoRequest(alice, gameId).status()).isEqualTo(200);
        Response draw = drawOffer(bob, gameId);
        assertThat(draw.status()).isEqualTo(409);
        assertThat(draw.text("$.code")).isEqualTo("REQUEST_ALREADY_PENDING");

        // Leave no pending undo behind: other tests share this database.
        assertThat(undoDecision(bob, gameId, false).status()).isEqualTo(200);
    }

    @Test
    void anOfferFreezesTheClockOfThePlayerWhoMustAnswer() throws Exception {
        Player alice = newPlayer();
        Player bob = newPlayer();
        String gameId = openGame(alice, bob, "RAPID");
        // Alice moves; now it is Bob's turn and his clock runs.
        move(alice, gameId, 1, 7, 7);

        drawOffer(alice, gameId);

        assertThat(snapshot(alice, gameId).has("$.clocks.running")).isFalse();

        drawDecision(bob, gameId, false);
        assertThat(snapshot(alice, gameId).text("$.clocks.running")).isEqualTo("WHITE");
    }

    @Test
    void aRankedDrawMovesTheRatingsLikeADraw() throws Exception {
        Player strong = newPlayer();
        Player weak = newPlayer();
        // Equal at first, so the band pairs them; the ratings differ by the time the
        // draw is agreed, which is what the settlement reads.
        rate(strong, 1500);
        rate(weak, 1500);
        quickMatch(strong, "FREESTYLE", "UNTIMED");
        Response matched = quickMatch(weak, "FREESTYLE", "UNTIMED");
        String gameId = matched.text("$.gameId");
        assertThat(matched.text("$.status")).isEqualTo("ACTIVE");
        // The room was opened as ANY, so the second player took black.
        assertThat(matched.text("$.you")).isEqualTo("BLACK");
        rate(weak, 1200);

        drawOffer(weak, gameId);
        assertThat(drawDecision(strong, gameId, true).status()).isEqualTo(200);

        Response view = snapshot(strong, gameId);
        assertThat(view.text("$.result")).isEqualTo("DRAW");
        assertThat(view.text("$.finishReason")).isEqualTo("AGREE");
        assertThat(view.number("$.black.ratingDelta")).isGreaterThan(0);
        assertThat(view.number("$.white.ratingDelta")).isLessThan(0);
    }

    /** Sets a player's rating directly, so the draw changes a known pair. */
    private void rate(Player player, int rating) {
        User user = users.findByNicknameIgnoreCase(player.nickname()).orElseThrow();
        user.setRating(rating);
        users.save(user);
    }
}