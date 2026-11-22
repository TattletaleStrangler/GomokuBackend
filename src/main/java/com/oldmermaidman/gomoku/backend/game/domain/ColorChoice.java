package com.oldmermaidman.gomoku.backend.game.domain;

/**
 * The colour the creator of a room wants to play.
 *
 * <p>{@code BLACK} and {@code WHITE} fix both seats; {@code ANY} leaves the colour to
 * the opponent who joins — the room is a private agreement, so the two players can also
 * arrange it in person before one of them enters the code.
 */
public enum ColorChoice {
    BLACK,
    WHITE,
    ANY
}