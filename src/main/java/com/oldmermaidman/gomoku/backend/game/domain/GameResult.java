package com.oldmermaidman.gomoku.backend.game.domain;

/** Outcome of a finished game, as stored in {@code games.result}. */
public enum GameResult {
    BLACK_WIN,
    WHITE_WIN,
    DRAW,
    /** The game is not finished. */
    NONE
}