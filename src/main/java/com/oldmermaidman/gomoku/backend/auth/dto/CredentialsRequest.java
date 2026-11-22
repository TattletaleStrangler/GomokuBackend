package com.oldmermaidman.gomoku.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration and login have the same shape, so they share this request.
 *
 * <p>BCrypt only looks at the first 72 bytes of a password, hence the upper bound: a
 * longer password would silently be truncated and two different passwords would open
 * the same account.
 */
public record CredentialsRequest(

        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_-]{3,24}$", message = "3-24 letters, digits, '_' or '-'")
        String nickname,

        @NotBlank
        @Size(min = 8, max = 72, message = "8-72 characters")
        String password) {
}