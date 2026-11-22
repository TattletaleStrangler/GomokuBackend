package com.oldmermaidman.gomoku.backend.auth.dto;

/** Win/loss/draw record over finished games; zeros for a new account. */
public record StatsResponse(long wins, long losses, long draws) {
}