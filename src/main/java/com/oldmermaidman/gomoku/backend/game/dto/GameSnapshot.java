package com.oldmermaidman.gomoku.backend.game.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The whole state of a game, as every game endpoint returns it
 * (TZ_BACKEND.md §7).
 *
 * <p>{@code you} is the caller's colour, {@code turn} is null when nobody is to move,
 * and the clock values are already the live ones at {@code serverTime}: the client
 * counts down from them instead of trusting the device clock.
 */
public record GameSnapshot(
        UUID gameId,
        String code,
        String rule,
        String timeControl,
        String status,
        PlayerView black,
        PlayerView white,
        String you,
        int ply,
        String turn,
        List<MoveView> moves,
        ClockView clocks,
        Instant serverTime,
        String result,
        String finishReason) {

    public record PlayerView(UUID userId, String nickname) {
    }

    public record MoveView(int ply, int x, int y, String color) {
    }

    /** Milliseconds left; both null for a game without clocks. */
    public record ClockView(Long blackRemainingMs, Long whiteRemainingMs, String running) {
    }
}