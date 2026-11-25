package com.oldmermaidman.gomoku.backend.user;

/** What an account may do beyond playing (FEATURES.md, B4). */
public enum UserRole {
    /** An ordinary player. */
    PLAYER,
    /** May open the admin panel and review reports. */
    ADMIN
}