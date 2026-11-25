package com.oldmermaidman.gomoku.backend.moderation;

/** Where a report is in the queue (FEATURES.md, B4). */
public enum ReportStatus {
    /** Nobody has looked at it yet. */
    NEW,
    /** Looked at, no decision needed. */
    REVIEWED,
    /** Something was done about it. */
    ACTIONED,
    /** Nothing was done: not a violation, or not enough to act on. */
    DISMISSED
}