package com.oldmermaidman.gomoku.backend.auth.dto;

import java.time.Instant;
import java.util.UUID;

/** The player as the client sees them. */
public record UserResponse(UUID id, String nickname, Instant createdAt) {
}