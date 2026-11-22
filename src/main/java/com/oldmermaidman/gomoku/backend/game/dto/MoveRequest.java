package com.oldmermaidman.gomoku.backend.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * One move. {@code ply} is the number the client expects to make, so a double tap or
 * a retry cannot place two stones.
 */
public record MoveRequest(

        @Min(1)
        int ply,

        @Min(0)
        @Max(14)
        int x,

        @Min(0)
        @Max(14)
        int y) {
}