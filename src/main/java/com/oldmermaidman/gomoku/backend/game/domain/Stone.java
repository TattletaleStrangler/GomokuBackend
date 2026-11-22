package com.oldmermaidman.gomoku.backend.game.domain;

/** A stone colour on the board; {@code EMPTY} is the absence of one. */
public enum Stone {

    BLACK,
    WHITE,
    EMPTY;

    /** The other colour; {@code EMPTY} maps to itself. */
    public Stone opponent() {
        return switch (this) {
            case BLACK -> WHITE;
            case WHITE -> BLACK;
            case EMPTY -> EMPTY;
        };
    }
}