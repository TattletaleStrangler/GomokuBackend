package com.oldmermaidman.gomoku.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT settings, bound from {@code app.jwt.*} (see application.yml and .env.example).
 *
 * <p>The secret never has a default: the application refuses to start without one,
 * so a misconfigured deployment fails immediately instead of issuing weak tokens.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {
}