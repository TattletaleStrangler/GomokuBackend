package com.oldmermaidman.gomoku.backend.game.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of a player's game history (FEATURES.md, B2).
 *
 * <p>{@code you} is the colour the caller played; {@code ratingDelta} is what a ranked
 * game changed for them (null for casual games); {@code moveCount} is enough to show
 * the list without loading every move. The moves themselves come from
 * {@code GET /games/{id}}, which the replay screen calls.
 */
public record GameHistoryView(
        UUID gameId,
        String rule,
        String timeControl,
        boolean ranked,
        String result,
        String finishReason,
        String you,
        String opponentNickname,
        Integer opponentRating,
        Integer ratingDelta,
        Instant finishedAt,
        int moveCount) {
}