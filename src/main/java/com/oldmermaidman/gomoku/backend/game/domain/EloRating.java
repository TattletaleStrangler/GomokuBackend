package com.oldmermaidman.gomoku.backend.game.domain;

/**
 * Elo (FEATURES.md, priority B2).
 *
 * <p>{@code K = 32} and a start of 1200: a win against an equal opponent is +16, and
 * beating a much stronger one approaches +32. Only ranked — that is, quick-play —
 * games go through here; a room by code stays casual.
 */
public final class EloRating {

    /** Where every account starts. */
    public static final int START = 1200;

    /** How far one game can move a rating at most. */
    public static final int K = 32;

    private EloRating() {
    }

    /**
     * The player's rating after a game against {@code opponent}.
     *
     * @param score 1 for a win, 0.5 for a draw, 0 for a loss
     */
    public static int after(int player, int opponent, double score) {
        double expected = 1.0 / (1.0 + Math.pow(10.0, (opponent - player) / 400.0));
        return (int) Math.round(player + K * (score - expected));
    }
}