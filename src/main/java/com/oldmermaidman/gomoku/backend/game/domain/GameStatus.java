package com.oldmermaidman.gomoku.backend.game.domain;

/** Lifecycle of a game (see V1__init.sql, {@code ck_games_status}). */
public enum GameStatus {
    /** A room is open and waits for an opponent. */
    WAITING,
    /** Two players, the clocks are running. */
    ACTIVE,
    /** The game has a result. */
    FINISHED,
    /** The room was closed or the game was abandoned without a result. */
    ABANDONED
}