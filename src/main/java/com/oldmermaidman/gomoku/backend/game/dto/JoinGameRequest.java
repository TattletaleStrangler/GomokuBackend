package com.oldmermaidman.gomoku.backend.game.dto;

import com.oldmermaidman.gomoku.backend.game.domain.ColorChoice;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Joining a room by the code shown to its creator.
 *
 * <p>{@code color} is a wish: it is honoured only when the creator left the colour open
 * ({@code ANY}). Otherwise the room's own choice fixes both seats and this field is
 * ignored. When omitted, an open room puts the joiner on black.
 */
public record JoinGameRequest(

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9]{6}", message = "six letters or digits")
        String code,

        ColorChoice color) {
}