package com.oldmermaidman.gomoku.backend.game.domain;

/**
 * Renju forbidden-move logic for black, plus the shared "five in a row" check.
 *
 * <p>A faithful port of the client's {@code ForbiddenMoves} (Kotlin), so both sides
 * judge a position the same way. The black stone is assumed to be placed already.
 * The three forbidden moves are:
 * <ul>
 *   <li><b>overline</b> — six or more stones in a row;</li>
 *   <li><b>double-four</b> — the move creates two or more fours;</li>
 *   <li><b>double-three</b> — the move creates two or more open (live) threes.</li>
 * </ul>
 *
 * <p>Detection uses a 13-cell window (±6) along each of the four directions: wide
 * enough to see any five containing the placed stone and to inspect its open ends.
 */
public final class ForbiddenMoves {

    /** The four directions: horizontal, vertical and the two diagonals. */
    static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    private static final int WINDOW_RADIUS = 6;
    private static final int WINDOW_LENGTH = WINDOW_RADIUS * 2 + 1;

    private ForbiddenMoves() {
    }

    /** Consecutive stones of {@code colour} on the line through (x, y) along (dx, dy). */
    public static int countLine(Board board, int x, int y, int dx, int dy, Stone colour) {
        int count = 1;

        int nx = x + dx;
        int ny = y + dy;
        while (board.inBounds(nx, ny) && board.at(nx, ny) == colour) {
            count++;
            nx += dx;
            ny += dy;
        }

        nx = x - dx;
        ny = y - dy;
        while (board.inBounds(nx, ny) && board.at(nx, ny) == colour) {
            count++;
            nx -= dx;
            ny -= dy;
        }
        return count;
    }

    /** Five or more in a row: the win of gomoku and of white in renju. */
    public static boolean hasFive(Board board, int x, int y, Stone colour) {
        for (int[] direction : DIRECTIONS) {
            if (countLine(board, x, y, direction[0], direction[1], colour) >= 5) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the black stone at (x, y) is a forbidden move. A genuine five is a
     * win, never a forbidden move, so it short-circuits.
     */
    public static boolean isForbidden(Board board, int x, int y) {
        if (isOverline(board, x, y)) {
            return true;
        }
        if (hasFive(board, x, y, Stone.BLACK)) {
            return false;
        }
        if (isDoubleFour(board, x, y)) {
            return true;
        }
        return isDoubleThree(board, x, y);
    }

    public static boolean isOverline(Board board, int x, int y) {
        for (int[] direction : DIRECTIONS) {
            if (countLine(board, x, y, direction[0], direction[1], Stone.BLACK) >= 6) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDoubleFour(Board board, int x, int y) {
        int fours = 0;
        for (int[] direction : DIRECTIONS) {
            if (directionHasFour(board, x, y, direction[0], direction[1])) {
                fours++;
            }
            if (fours >= 2) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDoubleThree(Board board, int x, int y) {
        int threes = 0;
        for (int[] direction : DIRECTIONS) {
            if (directionHasLiveThree(board, x, y, direction[0], direction[1])) {
                threes++;
            }
            if (threes >= 2) {
                return true;
            }
        }
        return false;
    }

    // ---- internals --------------------------------------------------------

    /**
     * A 13-character window centred on (x, y) along (dx, dy):
     * '1' black, '0' white, '.' empty, '#' board edge.
     */
    private static char[] window(Board board, int x, int y, int dx, int dy) {
        char[] cells = new char[WINDOW_LENGTH];
        for (int i = -WINDOW_RADIUS; i <= WINDOW_RADIUS; i++) {
            int nx = x + dx * i;
            int ny = y + dy * i;
            cells[i + WINDOW_RADIUS] = !board.inBounds(nx, ny)
                    ? '#'
                    : switch (board.at(nx, ny)) {
                        case BLACK -> '1';
                        case EMPTY -> '.';
                        case WHITE -> '0';
                    };
        }
        return cells;
    }

    /** A cell is open for extension when it is empty or already our stone. */
    private static boolean isOpen(char[] window, int index) {
        if (index < 0 || index >= window.length) {
            return true;
        }
        char cell = window[index];
        return cell == '.' || cell == '1';
    }

    /**
     * True when the placed stone creates a four here: some empty cell completes a
     * five-in-a-row that contains the placed stone.
     */
    private static boolean directionHasFour(Board board, int x, int y, int dx, int dy) {
        char[] window = window(board, x, y, dx, dy);
        for (int empty = 0; empty < window.length; empty++) {
            if (window[empty] != '.') {
                continue;
            }
            char[] completed = window.clone();
            completed[empty] = '1';
            if (containsFiveThrough(completed, WINDOW_RADIUS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the placed stone creates a live three here: some empty cell makes an
     * open four (".XXXX.") that contains the placed stone.
     */
    private static boolean directionHasLiveThree(Board board, int x, int y, int dx, int dy) {
        char[] window = window(board, x, y, dx, dy);
        for (int empty = 0; empty < window.length; empty++) {
            if (window[empty] != '.') {
                continue;
            }
            char[] completed = window.clone();
            completed[empty] = '1';
            if (containsOpenFourThrough(completed, WINDOW_RADIUS)) {
                return true;
            }
        }
        return false;
    }

    /** Any five consecutive '1' cells containing {@code centre}. */
    private static boolean containsFiveThrough(char[] cells, int centre) {
        for (int start = 0; start <= cells.length - 5; start++) {
            if (centre < start || centre > start + 4) {
                continue;
            }
            boolean five = true;
            for (int i = start; i < start + 5; i++) {
                if (cells[i] != '1') {
                    five = false;
                    break;
                }
            }
            if (five) {
                return true;
            }
        }
        return false;
    }

    /**
     * An open four (".XXXX.") containing {@code centre}: four stones with a single
     * empty cell inside the window of five and an open neighbour on the far side.
     */
    private static boolean containsOpenFourThrough(char[] cells, int centre) {
        for (int start = 0; start <= cells.length - 5; start++) {
            if (centre < start || centre > start + 4) {
                continue;
            }

            int stones = 0;
            int emptyIndex = -1;
            for (int i = start; i < start + 5; i++) {
                if (cells[i] == '1') {
                    stones++;
                } else if (cells[i] == '.') {
                    if (emptyIndex != -1) {
                        stones = -1;   // two empty cells: not a four
                        break;
                    }
                    emptyIndex = i;
                } else {
                    stones = -1;       // opponent stone or edge: not a four
                    break;
                }
            }

            if (stones == 4) {
                if (emptyIndex == start && isOpen(cells, start + 5)) {
                    return true;
                }
                if (emptyIndex == start + 4 && isOpen(cells, start - 1)) {
                    return true;
                }
            }
        }
        return false;
    }
}