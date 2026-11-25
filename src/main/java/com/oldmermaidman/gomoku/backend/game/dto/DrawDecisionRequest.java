package com.oldmermaidman.gomoku.backend.game.dto;

import jakarta.validation.constraints.NotNull;

/** The opponent's answer to a draw offer (FUTURE.md). */
public record DrawDecisionRequest(@NotNull Boolean accept) {
}