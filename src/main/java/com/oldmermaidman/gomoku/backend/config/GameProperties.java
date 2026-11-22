package com.oldmermaidman.gomoku.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Rules of the undo request (FEATURES.md, "Отмена хода в онлайн-партии").
 *
 * <p>{@code window} is how long after a move it may still be taken back,
 * {@code decisionTimeout} how long the opponent has to answer, {@code limit} how many
 * requests each player gets per game.
 */
@ConfigurationProperties(prefix = "app.game")
public record GameProperties(Undo undo) {

    public record Undo(Duration window, Duration decisionTimeout, int limit) {
    }
}