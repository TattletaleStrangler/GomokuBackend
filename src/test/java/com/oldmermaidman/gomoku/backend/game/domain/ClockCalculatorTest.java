package com.oldmermaidman.gomoku.backend.game.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The clock arithmetic on its own: no database, no HTTP. */
class ClockCalculatorTest {

    private static final Instant TURN_START = Instant.parse("2026-01-01T10:00:00Z");
    private static final long FIFTEEN_MINUTES = Duration.ofMinutes(15).toMillis();

    @Test
    void timeRunsDownWhileItIsThePlayersTurn() {
        long remaining = ClockCalculator.remainingAt(TURN_START.plusSeconds(5), TURN_START, FIFTEEN_MINUTES);

        assertThat(remaining).isEqualTo(FIFTEEN_MINUTES - 5_000);
    }

    @Test
    void aStoppedClockDoesNotRun() {
        long remaining = ClockCalculator.remainingAt(TURN_START.plus(Duration.ofHours(2)), null, 5_000);

        assertThat(remaining).isEqualTo(5_000);
    }

    @Test
    void theRemainingTimeNeverGoesBelowZero() {
        long remaining = ClockCalculator.remainingAt(TURN_START.plus(Duration.ofMinutes(20)), TURN_START, FIFTEEN_MINUTES);

        assertThat(remaining).isZero();
    }

    @Test
    void aMoveChargesTheStretchAndPaysTheIncrement() {
        // 10 s spent, +10 s increment: the clock is back where it was.
        assertThat(ClockCalculator.afterMove(TURN_START.plusSeconds(10), TURN_START, FIFTEEN_MINUTES, 10_000))
                .isEqualTo(FIFTEEN_MINUTES);

        // 30 s spent, +10 s increment: 20 s are gone.
        assertThat(ClockCalculator.afterMove(TURN_START.plusSeconds(30), TURN_START, FIFTEEN_MINUTES, 10_000))
                .isEqualTo(FIFTEEN_MINUTES - 20_000);
    }

    @Test
    void theFlagFallsWhenNothingIsLeft() {
        assertThat(ClockCalculator.hasFlagFallen(TURN_START.plusSeconds(899), TURN_START, FIFTEEN_MINUTES))
                .isFalse();
        assertThat(ClockCalculator.hasFlagFallen(TURN_START.plusSeconds(900), TURN_START, FIFTEEN_MINUTES))
                .isTrue();
        assertThat(ClockCalculator.hasFlagFallen(TURN_START.plusSeconds(1000), TURN_START, FIFTEEN_MINUTES))
                .isTrue();
    }
}