package com.oldmermaidman.gomoku.backend.game.dto;

import jakarta.validation.constraints.NotNull;

/** The opponent's answer to a draw offer (FEATURES.md). */
public record DrawDecisionRequest(@NotNull Boolean accept) {
}