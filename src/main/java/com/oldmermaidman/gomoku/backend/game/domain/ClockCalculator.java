package com.oldmermaidman.gomoku.backend.game.domain;

import java.time.Instant;

/**
 * The clock arithmetic of a timed game, kept apart from the database so it can be
 * tested on its own.
 *
 * <p>The server stores, for the player to move, how much time was left when their
 * turn started ({@code storedRemainingMs}) and when it started
 * ({@code turnStartedAt}). The live value is the stored one minus the running
 * stretch, and a move adds the increment after charging the stretch (FIDE).
 */
public final class ClockCalculator {

    private ClockCalculator() {
    }

    /** Time left now, never below zero. */
    public static long remainingAt(Instant now, Instant turnStartedAt, long storedRemainingMs) {
        if (turnStartedAt == null) {
            return Math.max(0L, storedRemainingMs);
        }
        long elapsed = Math.max(0L, now.toEpochMilli() - turnStartedAt.toEpochMilli());
        return Math.max(0L, storedRemainingMs - elapsed);
    }

    /** Time left after a move: the stretch is charged, then the increment is added. */
    public static long afterMove(Instant now, Instant turnStartedAt, long storedRemainingMs, long incrementMs) {
        return remainingAt(now, turnStartedAt, storedRemainingMs) + Math.max(0L, incrementMs);
    }

    /** True when the player to move has used up all their time. */
    public static boolean hasFlagFallen(Instant now, Instant turnStartedAt, long storedRemainingMs) {
        return remainingAt(now, turnStartedAt, storedRemainingMs) <= 0L;
    }
}