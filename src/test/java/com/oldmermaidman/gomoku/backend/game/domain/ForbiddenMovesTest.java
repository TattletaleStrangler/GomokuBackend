package com.oldmermaidman.gomoku.backend.game.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The building blocks of the rule check, tested directly. */
class ForbiddenMovesTest {

    private final Board board = new Board();

    private void black(int x, int y) {
        board.place(x, y, Stone.BLACK);
    }

    private void white(int x, int y) {
        board.place(x, y, Stone.WHITE);
    }

    @Test
    void aLineIsCountedInBothDirections() {
        black(3, 7);
        black(4, 7);
        black(5, 7);

        assertThat(ForbiddenMoves.countLine(board, 4, 7, 1, 0, Stone.BLACK)).isEqualTo(3);
    }

    @Test
    void aLineStopsAtTheBoardEdge() {
        black(0, 0);

        assertThat(ForbiddenMoves.countLine(board, 0, 0, 1, 0, Stone.BLACK)).isEqualTo(1);
        assertThat(ForbiddenMoves.countLine(board, 0, 0, 1, 1, Stone.BLACK)).isEqualTo(1);
    }

    @Test
    void anOpponentStoneBreaksTheLine() {
        black(3, 7);
        black(4, 7);
        white(5, 7);
        black(6, 7);

        assertThat(ForbiddenMoves.countLine(board, 4, 7, 1, 0, Stone.BLACK)).isEqualTo(2);
    }

    @Test
    void fiveIsAFiveAndFourIsNot() {
        for (int x = 3; x <= 6; x++) {
            black(x, 7);
        }
        assertThat(ForbiddenMoves.hasFive(board, 6, 7, Stone.BLACK)).isFalse();

        black(7, 7);
        assertThat(ForbiddenMoves.hasFive(board, 7, 7, Stone.BLACK)).isTrue();
    }

    @Test
    void sixInARowIsAnOverlineAndFiveIsNot() {
        for (int x = 3; x <= 7; x++) {
            black(x, 7);
        }
        assertThat(ForbiddenMoves.isOverline(board, 7, 7)).isFalse();

        black(8, 7);
        assertThat(ForbiddenMoves.isOverline(board, 8, 7)).isTrue();
        assertThat(ForbiddenMoves.isOverline(board, 7, 7)).isTrue();
    }

    @Test
    void aFiveIsNeverForbiddenEvenWhenItAlsoFormsFours() {
        for (int x = 3; x <= 7; x++) {
            black(x, 7);
        }

        assertThat(ForbiddenMoves.isForbidden(board, 7, 7)).isFalse();
    }

    @Test
    void aCrossingOfTwoOpenTwosIsADoubleThree() {
        black(5, 7);
        black(6, 7);
        black(7, 5);
        black(7, 6);
        black(7, 7);

        assertThat(ForbiddenMoves.isDoubleThree(board, 7, 7)).isTrue();
        assertThat(ForbiddenMoves.isDoubleFour(board, 7, 7)).isFalse();
        assertThat(ForbiddenMoves.isForbidden(board, 7, 7)).isTrue();
    }

    @Test
    void aCrossingOfTwoFoursIsADoubleFour() {
        black(4, 7);
        black(5, 7);
        black(6, 7);
        black(7, 4);
        black(7, 5);
        black(7, 6);
        black(7, 7);

        assertThat(ForbiddenMoves.isDoubleFour(board, 7, 7)).isTrue();
        assertThat(ForbiddenMoves.isForbidden(board, 7, 7)).isTrue();
    }
}