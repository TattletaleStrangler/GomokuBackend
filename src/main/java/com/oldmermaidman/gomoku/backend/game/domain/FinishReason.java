package com.oldmermaidman.gomoku.backend.game.domain;

/** Why a game ended, as stored in {@code games.finish_reason}. */
public enum FinishReason {
    /** Five in a row. */
    FIVE,
    /** Black played a forbidden move (renju). */
    FORBIDDEN,
    /** A player ran out of time. */
    TIMEOUT,
    /** A player resigned. */
    RESIGN,
    /** A player left the game or the room. */
    ABANDON,
    /** Both players agreed to a draw. */
    AGREE,
    /** Not finished, or finished as a draw. */
    NONE
}