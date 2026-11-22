package com.oldmermaidman.gomoku.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Confirms the password before the account is deleted. */
public record DeleteAccountRequest(@NotBlank String password) {
}