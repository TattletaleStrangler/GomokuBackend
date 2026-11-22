package com.oldmermaidman.gomoku.backend.game.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The golden cases of the game, taken from the client's test suite so that the server
 * and the Android app judge a position identically (TZ_BACKEND.md §10).
 */
class RefereeTest {

    private final Board board = new Board();

    private Referee.MoveResult play(int x, int y, Stone colour) {
        return Referee.apply(board, x, y, colour, Rule.RENJU);
    }

    private Referee.MoveResult play(int x, int y, Stone colour, Rule rule) {
        return Referee.apply(board, x, y, colour, rule);
    }

    /** Plays stones one by one, as the service does, and returns the last result. */
    private Referee.MoveResult playAll(Rule rule, Stone colour, int[][] cells) {
        Referee.MoveResult last = null;
        for (int[] cell : cells) {
            last = Referee.apply(board, cell[0], cell[1], colour, rule);
        }
        return last;
    }

    // ---- wins -------------------------------------------------------------

    @Test
    void fiveInARowWins() {
        Referee.MoveResult last = playAll(Rule.FREESTYLE, Stone.BLACK,
                new int[][]{{3, 7}, {4, 7}, {5, 7}, {6, 7}});

        assertThat(last.outcome()).isEqualTo(Referee.Outcome.OK);

        Referee.MoveResult winning = play(7, 7, Stone.BLACK);

        assertThat(winning.outcome()).isEqualTo(Referee.Outcome.WIN);
        assertThat(winning.winner()).isEqualTo(Stone.BLACK);
        assertThat(winning.winningLine()).hasSize(5);
        assertThat(winning.winningLine())
                .containsExactly(new Point(3, 7), new Point(4, 7), new Point(5, 7), new Point(6, 7), new Point(7, 7));
    }

    @Test
    void aVerticalFiveWins() {
        playAll(Rule.FREESTYLE, Stone.WHITE,
                new int[][]{{7, 3}, {7, 4}, {7, 5}, {7, 6}});

        Referee.MoveResult winning = play(7, 7, Stone.WHITE);

        assertThat(winning.outcome()).isEqualTo(Referee.Outcome.WIN);
        assertThat(winning.winner()).isEqualTo(Stone.WHITE);
    }

    @Test
    void aDiagonalFiveWins() {
        playAll(Rule.FREESTYLE, Stone.BLACK,
                new int[][]{{3, 3}, {4, 4}, {5, 5}, {6, 6}});

        Referee.MoveResult winning = play(7, 7, Stone.BLACK);

        assertThat(winning.outcome()).isEqualTo(Referee.Outcome.WIN);
        assertThat(winning.winner()).isEqualTo(Stone.BLACK);
    }

    // ---- renju forbidden moves -------------------------------------------

    @Test
    void anOverlineIsForbiddenAndNotAWin() {
        playAll(Rule.RENJU, Stone.BLACK,
                new int[][]{{3, 7}, {4, 7}, {5, 7}, {6, 7}, {7, 7}});

        Referee.MoveResult sixth = play(8, 7, Stone.BLACK);

        assertThat(sixth.outcome()).isEqualTo(Referee.Outcome.FORBIDDEN);
        assertThat(sixth.winner()).isEqualTo(Stone.WHITE);
        assertThat(sixth.winningLine()).isEmpty();
    }

    @Test
    void aDoubleThreeIsForbiddenForBlack() {
        // Two open twos crossing at (7,7): the crossing point makes a double three.
        playAll(Rule.RENJU, Stone.BLACK, new int[][]{{5, 7}, {6, 7}, {7, 5}, {7, 6}});

        Referee.MoveResult crossing = play(7, 7, Stone.BLACK);

        assertThat(crossing.outcome()).isEqualTo(Referee.Outcome.FORBIDDEN);
        assertThat(crossing.winner()).isEqualTo(Stone.WHITE);
    }

    @Test
    void theSameDoubleThreeIsAllowedInFreestyle() {
        playAll(Rule.FREESTYLE, Stone.BLACK, new int[][]{{5, 7}, {6, 7}, {7, 5}, {7, 6}});

        Referee.MoveResult crossing = play(7, 7, Stone.BLACK, Rule.FREESTYLE);

        assertThat(crossing.outcome()).isEqualTo(Referee.Outcome.OK);
    }

    @Test
    void aDoubleFourIsForbiddenForBlack() {
        // Horizontal and vertical open fours completed by the same stone.
        playAll(Rule.RENJU, Stone.BLACK,
                new int[][]{{4, 7}, {5, 7}, {6, 7}, {7, 4}, {7, 5}, {7, 6}});

        Referee.MoveResult crossing = play(7, 7, Stone.BLACK);

        assertThat(crossing.outcome()).isEqualTo(Referee.Outcome.FORBIDDEN);
        assertThat(crossing.winner()).isEqualTo(Stone.WHITE);
    }

    @Test
    void whiteHasNoForbiddenMoves() {
        playAll(Rule.RENJU, Stone.WHITE, new int[][]{{5, 7}, {6, 7}, {7, 5}, {7, 6}});

        Referee.MoveResult crossing = play(7, 7, Stone.WHITE);

        assertThat(crossing.outcome()).isEqualTo(Referee.Outcome.OK);
    }

    @Test
    void fiveWinsEvenWhenTheMoveWouldAlsoBeADoubleThree() {
        // The stone completes a five on its row and, at the same time, opens two
        // live threes (vertical and diagonal). The five must win.
        playAll(Rule.RENJU, Stone.BLACK,
                new int[][]{{3, 7}, {4, 7}, {5, 7}, {6, 7}, {7, 5}, {7, 6}, {5, 5}, {6, 6}});
        assertThat(ForbiddenMoves.isDoubleThree(boardAfter(7, 7), 7, 7))
                .as("the position is a double three on its own")
                .isTrue();

        Referee.MoveResult result = play(7, 7, Stone.BLACK);

        assertThat(result.outcome()).isEqualTo(Referee.Outcome.WIN);
        assertThat(result.winner()).isEqualTo(Stone.BLACK);
    }

    /** A copy of the board with one extra black stone, for checking a helper alone. */
    private Board boardAfter(int x, int y) {
        Board copy = new Board();
        for (int cy = 0; cy < Board.SIZE; cy++) {
            for (int cx = 0; cx < Board.SIZE; cx++) {
                if (board.at(cx, cy) != Stone.EMPTY) {
                    copy.place(cx, cy, board.at(cx, cy));
                }
            }
        }
        copy.place(x, y, Stone.BLACK);
        return copy;
    }

    // ---- draw and rejects ------------------------------------------------

    @Test
    void aFullBoardWithoutFiveIsADraw() {
        Referee.MoveResult last = null;
        // Colours from (x + 2y) mod 4: runs of at most two in every direction.
        for (int y = 0; y < Board.SIZE; y++) {
            for (int x = 0; x < Board.SIZE; x++) {
                Stone colour = ((x + 2 * y) % 4) < 2 ? Stone.BLACK : Stone.WHITE;
                last = Referee.apply(board, x, y, colour, Rule.FREESTYLE);
                assertThat(last.outcome()).isIn(Referee.Outcome.OK, Referee.Outcome.DRAW);
            }
        }

        assertThat(board.isFull()).isTrue();
        assertThat(last).isNotNull();
        assertThat(last.outcome()).isEqualTo(Referee.Outcome.DRAW);
        assertThat(last.winner()).isNull();
    }

    @Test
    void anOccupiedCellIsRejected() {
        play(7, 7, Stone.BLACK);

        assertThat(play(7, 7, Stone.WHITE).outcome()).isEqualTo(Referee.Outcome.OCCUPIED);
    }

    @Test
    void coordinatesOutsideTheBoardAreRejected() {
        assertThat(play(-1, 0, Stone.BLACK).outcome()).isEqualTo(Referee.Outcome.INVALID);
        assertThat(play(15, 14, Stone.BLACK).outcome()).isEqualTo(Referee.Outcome.INVALID);
        assertThat(play(0, 15, Stone.BLACK).outcome()).isEqualTo(Referee.Outcome.INVALID);
    }

    @Test
    void aRejectedMoveDoesNotChangeTheBoard() {
        play(7, 7, Stone.BLACK);

        play(7, 7, Stone.WHITE);
        play(-1, 0, Stone.BLACK);

        assertThat(board.stoneCount()).isEqualTo(1);
        assertThat(board.at(7, 7)).isEqualTo(Stone.BLACK);
    }

    @Test
    void theWinningLineIsOnlyTheRunItself() {
        List<Point> line = Referee.winningLine(prepareFive(), 7, 7, Stone.BLACK);

        assertThat(line).hasSize(5);
        assertThat(line.getFirst()).isEqualTo(new Point(3, 7));
        assertThat(line.getLast()).isEqualTo(new Point(7, 7));
    }

    private Board prepareFive() {
        for (int x = 3; x <= 7; x++) {
            board.place(x, 7, Stone.BLACK);
        }
        return board;
    }
}