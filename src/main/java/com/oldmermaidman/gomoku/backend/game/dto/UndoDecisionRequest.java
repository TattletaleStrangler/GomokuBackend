package com.oldmermaidman.gomoku.backend.game.dto;

import jakarta.validation.constraints.NotNull;

/** The opponent's answer to an undo request. */
public record UndoDecisionRequest(@NotNull Boolean accept) {
}