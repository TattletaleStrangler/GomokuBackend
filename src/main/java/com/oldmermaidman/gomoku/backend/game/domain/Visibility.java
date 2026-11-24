package com.oldmermaidman.gomoku.backend.game.domain;

/**
 * Who may see a room.
 *
 * <p>{@code PRIVATE} rooms are shared by code and never listed. {@code PUBLIC} ones are
 * shown in the lobby and are the rooms quick match pairs players into.
 */
public enum Visibility {
    PRIVATE,
    PUBLIC
}