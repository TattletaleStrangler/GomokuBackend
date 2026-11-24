package com.oldmermaidman.gomoku.backend.moderation;

/** Why a player is reported (FUTURE.md). A short list, not free text. */
public enum ReportReason {
    /** The nickname itself. */
    NICKNAME,
    /** Dragging the game out on purpose. */
    STALLING,
    /** Suspected engine use or another way of cheating. */
    CHEATING,
    /** Anything else; the game is attached, so an admin can look. */
    OTHER
}