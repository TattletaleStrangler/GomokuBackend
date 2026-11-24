package com.oldmermaidman.gomoku.backend.moderation.dto;

import com.oldmermaidman.gomoku.backend.moderation.ReportReason;
import jakarta.validation.constraints.NotNull;

/** A complaint about the opponent of the game in the path. */
public record ReportRequest(@NotNull ReportReason reason) {
}