package com.oldmermaidman.gomoku.backend.auth.dto;

import java.time.Instant;
import java.util.UUID;

/** The player plus their record, returned by {@code GET /api/v1/me}. */
public record ProfileResponse(
        UUID id, String nickname, int rating, Instant createdAt, StatsResponse stats) {
}