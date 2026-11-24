package com.oldmermaidman.gomoku.backend.user.dto;

import java.util.UUID;

/**
 * One row of the leaderboard (FEATURES.md, B2): a player with at least one finished
 * game, ordered by Elo. {@code rank} starts at 1 and is sequential, so two players on
 * the same rating still have distinct places.
 */
public record LeaderboardRow(
        int rank,
        UUID userId,
        String nickname,
        int rating,
        long wins,
        long losses,
        long draws) {
}