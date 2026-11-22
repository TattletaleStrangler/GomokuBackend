package com.oldmermaidman.gomoku.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The refresh token handed back by registration or login. */
public record RefreshTokenRequest(

        @NotBlank
        @Size(min = 32, max = 128)
        String refreshToken) {
}