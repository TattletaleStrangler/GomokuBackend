package com.oldmermaidman.gomoku.backend.game.domain;

/**
 * The 15×15 board (see TZ_BACKEND.md §4: one size only).
 *
 * <p>Coordinates are zero-based and match the client and the Gomocup convention:
 * {@code (0,0)} is the top-left corner, {@code (7,7)} the centre.
 */
public final class Board {

    public static final int SIZE = 15;

    private final Stone[][] cells;
    private int stones;

    public Board() {
        this(SIZE);
    }

    public Board(int size) {
        this.cells = new Stone[size][size];
        for (Stone[] row : cells) {
            java.util.Arrays.fill(row, Stone.EMPTY);
        }
    }

    public int size() {
        return cells.length;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < size() && y < size();
    }

    /** The stone at a cell; the coordinates must be inside the board. */
    public Stone at(int x, int y) {
        if (!inBounds(x, y)) {
            throw new IllegalArgumentException("(" + x + "," + y + ") is outside the board");
        }
        return cells[y][x];
    }

    public boolean isEmpty(int x, int y) {
        return inBounds(x, y) && cells[y][x] == Stone.EMPTY;
    }

    /** Places a stone; the cell must be empty and inside the board. */
    public void place(int x, int y, Stone stone) {
        if (!inBounds(x, y)) {
            throw new IllegalArgumentException("(" + x + "," + y + ") is outside the board");
        }
        if (cells[y][x] != Stone.EMPTY) {
            throw new IllegalStateException("(" + x + "," + y + ") is already taken");
        }
        cells[y][x] = stone;
        stones++;
    }

    /** Removes a stone (used by tests and by move rollback); the cell must be taken. */
    public void remove(int x, int y) {
        if (!inBounds(x, y) || cells[y][x] == Stone.EMPTY) {
            throw new IllegalStateException("(" + x + "," + y + ") is already empty");
        }
        cells[y][x] = Stone.EMPTY;
        stones--;
    }

    public int stoneCount() {
        return stones;
    }

    public boolean isFull() {
        return stones == size() * size();
    }
}