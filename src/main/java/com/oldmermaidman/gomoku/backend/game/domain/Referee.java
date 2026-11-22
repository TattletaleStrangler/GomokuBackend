package com.oldmermaidman.gomoku.backend.game.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a move and judges the position — the single authority on the outcome of a
 * game (TZ_BACKEND.md §10).
 *
 * <p>The order of the checks is the same as in the client, because it decides real
 * results: overline first (it is never a win), then five (it beats every other
 * forbidden move), then double-four and double-three, then the draw.
 */
public final class Referee {

    /** What happened when a stone was played. */
    public enum Outcome {
        /** The game continues. */
        OK,
        /** Five in a row: {@code winner} took the game. */
        WIN,
        /** The board is full and nobody has five. */
        DRAW,
        /** Black played a forbidden move: white wins. */
        FORBIDDEN,
        /** Outside the board. */
        INVALID,
        /** The cell is taken. */
        OCCUPIED
    }

    /** The result of one move: the board is already updated when this is returned. */
    public record MoveResult(Outcome outcome, Stone winner, List<Point> winningLine) {

        static MoveResult of(Outcome outcome) {
            return new MoveResult(outcome, null, List.of());
        }

        public boolean isGameOver() {
            return outcome == Outcome.WIN || outcome == Outcome.DRAW || outcome == Outcome.FORBIDDEN;
        }
    }

    private Referee() {
    }

    /**
     * Places the stone and judges the position.
     *
     * @throws IllegalStateException when the board already holds a stone there — use
     *                               {@link MoveResult#outcome()} checks instead by
     *                               asking {@link Board#isEmpty(int, int)} first
     */
    public static MoveResult apply(Board board, int x, int y, Stone colour, Rule rule) {
        if (!board.inBounds(x, y)) {
            return MoveResult.of(Outcome.INVALID);
        }
        if (!board.isEmpty(x, y)) {
            return MoveResult.of(Outcome.OCCUPIED);
        }

        board.place(x, y, colour);
        boolean renju = rule == Rule.RENJU;

        // 1. Overline: six or more in a row is never a win for black.
        if (colour == Stone.BLACK && renju && ForbiddenMoves.isOverline(board, x, y)) {
            return new MoveResult(Outcome.FORBIDDEN, Stone.WHITE, List.of());
        }

        // 2. Five wins, and it wins over every other forbidden move.
        if (ForbiddenMoves.hasFive(board, x, y, colour)) {
            return new MoveResult(Outcome.WIN, colour, winningLine(board, x, y, colour));
        }

        // 3. The remaining renju forbidden moves.
        if (colour == Stone.BLACK && renju
                && (ForbiddenMoves.isDoubleFour(board, x, y) || ForbiddenMoves.isDoubleThree(board, x, y))) {
            return new MoveResult(Outcome.FORBIDDEN, Stone.WHITE, List.of());
        }

        // 4. A full board without five is a draw.
        if (board.isFull()) {
            return MoveResult.of(Outcome.DRAW);
        }

        return MoveResult.of(Outcome.OK);
    }

    /**
     * The run of five or more stones of {@code colour} through (x, y), in board order.
     * Empty when there is no such run.
     */
    public static List<Point> winningLine(Board board, int x, int y, Stone colour) {
        for (int[] direction : ForbiddenMoves.DIRECTIONS) {
            int dx = direction[0];
            int dy = direction[1];
            if (ForbiddenMoves.countLine(board, x, y, dx, dy, colour) < 5) {
                continue;
            }

            int startX = x;
            int startY = y;
            while (board.inBounds(startX - dx, startY - dy) && board.at(startX - dx, startY - dy) == colour) {
                startX -= dx;
                startY -= dy;
            }

            List<Point> line = new ArrayList<>();
            int currentX = startX;
            int currentY = startY;
            while (board.inBounds(currentX, currentY) && board.at(currentX, currentY) == colour) {
                line.add(new Point(currentX, currentY));
                currentX += dx;
                currentY += dy;
            }
            return line;
        }
        return List.of();
    }
}