package com.oldmermaidman.gomoku.backend.game.domain;

/**
 * The rule set of a game.
 *
 * <p>{@code FREESTYLE} is plain gomoku: five or more in a row wins for either side.
 * {@code RENJU} adds the forbidden moves for black (overline, double-four,
 * double-three) — see {@link ForbiddenMoves}.
 */
public enum Rule {
    FREESTYLE,
    RENJU
}