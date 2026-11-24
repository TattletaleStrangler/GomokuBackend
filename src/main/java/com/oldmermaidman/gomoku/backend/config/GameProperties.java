package com.oldmermaidman.gomoku.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Rules of the undo request (FEATURES.md, "Отмена хода в онлайн-партии") and of the
 * public rooms quick match uses.
 *
 * <p>{@code window} is how long after a move it may still be taken back,
 * {@code decisionTimeout} how long the opponent has to answer, {@code limit} how many
 * requests each player gets per game; {@code roomTtl} how long a public room nobody
 * joined stays in the lobby.
 */
@ConfigurationProperties(prefix = "app.game")
public record GameProperties(Undo undo, Matchmaking matchmaking) {

    public record Undo(Duration window, Duration decisionTimeout, int limit) {
    }

    public record Matchmaking(@DefaultValue("15m") Duration roomTtl) {
    }
}