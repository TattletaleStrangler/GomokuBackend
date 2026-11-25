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
public record GameProperties(Undo undo, Matchmaking matchmaking, Draw draw) {

    public record Undo(Duration window, Duration decisionTimeout, int limit) {
    }

    public record Matchmaking(
            @DefaultValue("15m") Duration roomTtl,
            @DefaultValue("200") int bandBase,
            @DefaultValue("50") int bandStep,
            @DefaultValue("5") int bandStepSeconds,
            @DefaultValue("600") int bandMax) {
    }

    /**
     * A draw offer has the same shape as an undo request, but the answer window is
     * longer: agreeing to a draw is a decision, not a misclick to correct.
     */
    public record Draw(Duration decisionTimeout, int limit) {
    }
}