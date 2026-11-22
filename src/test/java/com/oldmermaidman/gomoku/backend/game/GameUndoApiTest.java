package com.oldmermaidman.gomoku.backend.game;

import com.oldmermaidman.gomoku.backend.testsupport.GameTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asking to take a move back (FEATURES.md, "Отмена хода в онлайн-партии"): the window
 * after the move, the per-move and per-game limits, the frozen clock, the ghost stone
 * and the automatic refusal when nobody answers.
 */
class GameUndoApiTest extends GameTestSupport {

    private static final int FIFTEEN_MINUTES = 15 * 60_000;

    @Test
    void anAcceptedUndoReturnsTheMoveAndLeavesAGhostForItsOwnerOnly() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();
        String gameId = openGame(black, white, "RAPID");
        assertThat(move(black, gameId, 1, 7, 7).status()).isEqualTo(200);

        Response requested = undoRequest(black, gameId);

        assertThat(requested.status()).isEqualTo(200);
        assertThat(requested.text("$.undo.requestedBy")).isEqualTo("BLACK");
        assertThat(requested.number("$.undo.ply")).isEqualTo(1);
        assertThat(requested.number("$.undoRemaining")).isEqualTo(2);   // the attempt is spent
        assertThat(requested.text("$.turn")).isEqualTo("WHITE");        // white answers
        assertThat(requested.has("$.clocks.running")).isFalse();        // both clocks are frozen

        Response accepted = undoDecision(white, gameId, true);

        assertThat(accepted.status()).isEqualTo(200);
        assertThat(accepted.text("$.status")).isEqualTo("ACTIVE");
        assertThat(accepted.number("$.ply")).isEqualTo(1);
        assertThat(accepted.number("$.moves.length()")).isZero();
        assertThat(accepted.text("$.turn")).isEqualTo("BLACK");
        assertThat(accepted.has("$.undo")).isFalse();
        // The ghost belongs to the player whose stone was taken back; the opponent is
        // not told about it at all, so no client can draw it for them.
        assertThat(accepted.has("$.lastUndoneMove")).isFalse();

        Response ownerView = snapshot(black, gameId);
        assertThat(ownerView.number("$.lastUndoneMove.x")).isEqualTo(7);
        assertThat(ownerView.number("$.lastUndoneMove.y")).isEqualTo(7);
        assertThat(ownerView.text("$.lastUndoneMove.color")).isEqualTo("BLACK");
        // The move had earned its increment; taking it back takes the increment too.
        assertThat(ownerView.number("$.clocks.blackRemainingMs")).isEqualTo(FIFTEEN_MINUTES);

        // The ghost lives until its owner plays again.
        assertThat(move(black, gameId, 1, 7, 7).status()).isEqualTo(200);
        Response afterNewStone = snapshot(black, gameId);
        assertThat(afterNewStone.has("$.lastUndoneMove")).isFalse();
        assertThat(afterNewStone.number("$.moves.length()")).isEqualTo(1);
    }

    @Test
    void aRefusalKeepsTheMoveAndSpendsTheAttempt() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();
        String gameId = openGame(black, white, "RAPID");
        move(black, gameId, 1, 7, 7);

        undoRequest(black, gameId);
        Response refused = undoDecision(white, gameId, false);

        assertThat(refused.status()).isEqualTo(200);
        assertThat(refused.has("$.undo")).isFalse();
        assertThat(refused.number("$.moves.length()")).isEqualTo(1);
        assertThat(refused.text("$.turn")).isEqualTo("WHITE");
        assertThat(refused.has("$.lastUndoneMove")).isFalse();
        // The refusal response is the opponent's snapshot: their own attempts are
        // untouched, while the requester has spent one.
        assertThat(refused.number("$.undoRemaining")).isEqualTo(3);
        assertThat(snapshot(black, gameId).number("$.undoRemaining")).isEqualTo(2);
        assertThat(refused.text("$.clocks.running")).isEqualTo("WHITE");

        // The same move cannot be asked about twice.
        Response again = undoRequest(black, gameId);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.text("$.code")).isEqualTo("UNDO_ALREADY_REQUESTED");
    }

    @Test
    void anUnansweredRequestIsRefusedWhenItsTimeRunsOut() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();
        String gameId = openGame(black, white, "RAPID");
        move(black, gameId, 1, 7, 7);
        undoRequest(black, gameId);

        advanceClock(Duration.ofSeconds(4));
        assertThat(games.expireUndoRequests()).isEqualTo(1);

        Response after = snapshot(white, gameId);

        assertThat(after.has("$.undo")).isFalse();
        assertThat(after.text("$.turn")).isEqualTo("WHITE");
        assertThat(after.text("$.clocks.running")).isEqualTo("WHITE");
        assertThat(after.number("$.undoRemaining")).isEqualTo(3);   // white's own attempts
    }

    @Test
    void anAnswerAfterTheDeadlineCountsAsRefusal() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();
        String gameId = openGame(black, white, "RAPID");
        move(black, gameId, 1, 7, 7);
        undoRequest(black, gameId);

        advanceClock(Duration.ofSeconds(4));
        Response late = undoDecision(white, gameId, true);

        assertThat(late.status()).isEqualTo(200);
        assertThat(late.number("$.moves.length()")).isEqualTo(1);
        assertThat(late.has("$.undo")).isFalse();
        assertThat(late.has("$.lastUndoneMove")).isFalse();
    }

    @Test
    void askingTooLateIsRejected() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();
        String gameId = openGame(black, white, "RAPID");
        move(black, gameId, 1, 7, 7);

        advanceClock(Duration.ofSeconds(3));   // the window is two seconds

        Response late = undoRequest(black, gameId);

        assertThat(late.status()).isEqualTo(409);
        assertThat(late.text("$.code")).isEqualTo("UNDO_TOO_LATE");
    }

    @Test
    void threeRefusalsExhaustTheAttempts() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();
        String gameId = openGame(black, white, "RAPID");

        int ply = 1;
        int[][] blackCells = {{0, 0}, {2, 0}, {4, 0}, {6, 0}};
        int[][] whiteCells = {{1, 1}, {3, 1}, {5, 1}};

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(move(black, gameId, ply++, blackCells[attempt - 1][0], blackCells[attempt - 1][1]).status())
                    .isEqualTo(200);

            Response requested = undoRequest(black, gameId);
            assertThat(requested.status()).as("attempt %s", attempt).isEqualTo(200);
            assertThat(requested.number("$.undoRemaining")).as("attempt %s", attempt).isEqualTo(3 - attempt);

            undoDecision(white, gameId, false);
            assertThat(move(white, gameId, ply++, whiteCells[attempt - 1][0], whiteCells[attempt - 1][1]).status())
                    .isEqualTo(200);
        }

        assertThat(move(black, gameId, ply, blackCells[3][0], blackCells[3][1]).status()).isEqualTo(200);
        Response blocked = undoRequest(black, gameId);

        assertThat(blocked.status()).isEqualTo(409);
        assertThat(blocked.text("$.code")).isEqualTo("UNDO_LIMIT_REACHED");
        assertThat(snapshot(black, gameId).number("$.undoRemaining")).isZero();
    }

    @Test
    void onlyTheOpponentDecides() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();
        String gameId = openGame(black, white, "RAPID");
        move(black, gameId, 1, 7, 7);
        undoRequest(black, gameId);

        Response self = undoDecision(black, gameId, true);

        assertThat(self.status()).isEqualTo(409);
        assertThat(self.text("$.code")).isEqualTo("UNDO_NOT_REQUESTED");

        // The request is still pending and the opponent can answer it.
        assertThat(undoDecision(white, gameId, true).status()).isEqualTo(200);
    }

    @Test
    void decidingWithoutARequestIsRejected() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();
        String gameId = openGame(black, white, "RAPID");

        Response decision = undoDecision(white, gameId, true);

        assertThat(decision.status()).isEqualTo(409);
        assertThat(decision.text("$.code")).isEqualTo("UNDO_NOT_REQUESTED");
    }

    @Test
    void askingWithoutAMoveOfYourOwnIsRejected() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();
        String gameId = openGame(black, white, "RAPID");

        Response requested = undoRequest(black, gameId);

        assertThat(requested.status()).isEqualTo(409);
        assertThat(requested.text("$.code")).isEqualTo("UNDO_NOT_ALLOWED");
    }

    @Test
    void theClockStaysFrozenAndMovesWaitWhileTheRequestIsPending() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();
        String gameId = openGame(black, white, "RAPID");
        move(black, gameId, 1, 7, 7);

        Response requested = undoRequest(black, gameId);
        long frozenWhite = requested.number("$.clocks.whiteRemainingMs");

        advanceClock(Duration.ofSeconds(2));
        Response later = snapshot(white, gameId);

        assertThat(later.number("$.clocks.whiteRemainingMs")).isEqualTo(frozenWhite);

        Response attemptToMove = move(white, gameId, 2, 0, 0);
        assertThat(attemptToMove.status()).isEqualTo(409);
        assertThat(attemptToMove.text("$.code")).isEqualTo("UNDO_ALREADY_REQUESTED");
    }

    @Test
    void aFinishedGameCannotBeTakenBack() throws Exception {
        Player black = newPlayer();
        Player white = newPlayer();
        String gameId = openGame(black, white, "RAPID");

        int[][] blackStones = {{3, 7}, {4, 7}, {5, 7}, {6, 7}, {7, 7}};
        int[][] whiteStones = {{0, 0}, {2, 2}, {4, 4}, {6, 6}};
        for (int i = 0; i < whiteStones.length; i++) {
            move(black, gameId, 2 * i + 1, blackStones[i][0], blackStones[i][1]);
            move(white, gameId, 2 * i + 2, whiteStones[i][0], whiteStones[i][1]);
        }
        assertThat(move(black, gameId, 9, 7, 7).text("$.status")).isEqualTo("FINISHED");

        Response requested = undoRequest(black, gameId);

        assertThat(requested.status()).isEqualTo(409);
        assertThat(requested.text("$.code")).isEqualTo("GAME_FINISHED");
    }
}