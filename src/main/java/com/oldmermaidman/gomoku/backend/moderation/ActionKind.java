package com.oldmermaidman.gomoku.backend.moderation;

/** What an administrator did (FEATURES.md, B4). Kept forever, with the report it came from. */
public enum ActionKind {
    /** Marked as seen without a decision. */
    REVIEW,
    /** Not a violation, or not provable. */
    DISMISS,
    /** A warning on the account; the player is not restricted yet. */
    WARN,
    /** Cannot use quick match until a moment; rooms by code still work. */
    BAN_RANKED,
    /** Cannot sign in at all. */
    BAN_ACCOUNT,
    /** Every active ban on the account ends now. */
    UNBAN
}