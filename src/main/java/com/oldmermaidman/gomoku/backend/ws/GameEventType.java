package com.oldmermaidman.gomoku.backend.ws;

/** Event kinds pushed over the socket (TZ_BACKEND.md §8). */
public enum GameEventType {
    /** The whole game state, for a fresh or reconnected client. */
    GAME_STATE,
    OPPONENT_JOINED,
    MOVE_APPLIED,
    GAME_FINISHED,
    OPPONENT_LEFT,
    /** Someone asked to take their last move back; the opponent has three seconds. */
    UNDO_REQUESTED,
    /** The request was refused, or nobody answered in time. */
    UNDO_DECIDED,
    /** The request was accepted: the move is gone and the ghost marks where it was. */
    UNDO_APPLIED,
    /** A draw was offered. */
    DRAW_OFFERED,
    /** The offer was answered: accepted or refused. */
    DRAW_DECIDED
}