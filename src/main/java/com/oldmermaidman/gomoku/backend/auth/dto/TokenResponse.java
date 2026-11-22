package com.oldmermaidman.gomoku.backend.auth.dto;

/**
 * What registration, login and refresh return.
 *
 * <p>{@code expiresIn} is the lifetime of the access token in seconds, so the client
 * does not have to parse the token to know when to refresh.
 */
public record TokenResponse(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
}