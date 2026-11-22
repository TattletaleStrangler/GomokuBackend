package com.oldmermaidman.gomoku.backend.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Joining a room by the code shown to its creator. */
public record JoinGameRequest(

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9]{6}", message = "six letters or digits")
        String code) {
}