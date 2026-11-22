package com.oldmermaidman.gomoku.backend.game.domain;

import java.time.Duration;

/**
 * The game clocks offered to a room's creator (TZ_BACKEND.md §4, same values as the
 * client's {@code TimeControl}).
 *
 * <p>The values follow FIDE's classification: classical 30+30, rapid 15+10, blitz
 * 3+2; {@code UNTIMED} is casual play without a clock.
 */
public enum TimeControl {

    CLASSIC(Duration.ofMinutes(30), Duration.ofSeconds(30)),
    RAPID(Duration.ofMinutes(15), Duration.ofSeconds(10)),
    BLITZ(Duration.ofMinutes(3), Duration.ofSeconds(2)),
    UNTIMED(null, null);

    private final Duration mainTime;
    private final Duration increment;

    TimeControl(Duration mainTime, Duration increment) {
        this.mainTime = mainTime;
        this.increment = increment;
    }

    public boolean isTimed() {
        return mainTime != null;
    }

    /** Main time in milliseconds; 0 for {@code UNTIMED}. */
    public long mainTimeMs() {
        return mainTime == null ? 0L : mainTime.toMillis();
    }

    /** Increment added after a player's move; 0 for {@code UNTIMED}. */
    public long incrementMs() {
        return increment == null ? 0L : increment.toMillis();
    }
}