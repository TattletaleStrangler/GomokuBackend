package com.oldmermaidman.gomoku.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Limits of the login endpoints (TZ_BACKEND.md §9).
 *
 * <p>{@code perIp} caps every {@code /api/v1/auth/*} call from one address;
 * {@code maxFailedLogins} blocks guessing passwords for one nickname.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(int perIp, Duration ipWindow, int maxFailedLogins, Duration failedLoginWindow) {
}