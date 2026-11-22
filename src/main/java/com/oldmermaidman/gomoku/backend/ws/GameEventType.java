package com.oldmermaidman.gomoku.backend.ws;

/** Event kinds pushed over the socket (TZ_BACKEND.md §8). */
public enum GameEventType {
    /** The whole game state, for a fresh or reconnected client. */
    GAME_STATE,
    OPPONENT_JOINED,
    MOVE_APPLIED,
    GAME_FINISHED,
    OPPONENT_LEFT
}