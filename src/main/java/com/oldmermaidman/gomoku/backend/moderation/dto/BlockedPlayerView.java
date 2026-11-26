package com.oldmermaidman.gomoku.backend.moderation.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry of the block list (FEATURES.md).
 *
 * <p>The nickname is what the player recognises; the id is what unblocking needs, so the
 * client does not have to look the account up again.
 */
public record BlockedPlayerView(UUID userId, String nickname, Instant blockedAt) {
}