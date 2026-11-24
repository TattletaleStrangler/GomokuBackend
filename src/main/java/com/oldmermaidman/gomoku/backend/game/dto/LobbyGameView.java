package com.oldmermaidman.gomoku.backend.game.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the open-games list (FEATURES.md, priority B1).
 *
 * <p>The code is included because the room is public by definition: it is how the
 * client joins, with the same {@code POST /games/join} it uses for a private room.
 * {@code creatorColor} tells the joiner what the creator picked — {@code ANY} means
 * the joiner's own wish decides. {@code creatorRating} is their Elo, and
 * {@code ranked} says whether the game will settle it.
 */
public record LobbyGameView(
        UUID gameId,
        String code,
        String rule,
        String timeControl,
        String creatorColor,
        String creatorNickname,
        Integer creatorRating,
        boolean ranked,
        Instant createdAt) {
}