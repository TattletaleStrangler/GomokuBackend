package com.oldmermaidman.gomoku.backend.moderation;

/** How far a ban reaches (FEATURES.md, B4). */
public enum BanKind {
    /** Quick match is closed; rooms by code still work, so friends can play. */
    RANKED,
    /** Signing in is refused altogether. */
    ACCOUNT
}