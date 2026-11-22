package com.oldmermaidman.gomoku.backend.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sliding window itself: counting, expiry, isolation and eviction. */
class RateLimiterTest {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    private long now = 1_000_000L;
    private final RateLimiter limiter = new RateLimiter(() -> now);

    @Test
    void allowsUpToTheLimit() {
        assertTrue(limiter.tryAcquire("k", 3, MINUTE));
        assertTrue(limiter.tryAcquire("k", 3, MINUTE));
        assertTrue(limiter.tryAcquire("k", 3, MINUTE));

        assertFalse(limiter.tryAcquire("k", 3, MINUTE));
    }

    @Test
    void theWindowSlides() {
        assertTrue(limiter.tryAcquire("k", 2, Duration.ofSeconds(10)));

        now += 5_000;
        assertTrue(limiter.tryAcquire("k", 2, Duration.ofSeconds(10)));
        assertFalse(limiter.tryAcquire("k", 2, Duration.ofSeconds(10)));

        // The first hit left the window; the second one is still inside.
        now += 6_000;
        assertTrue(limiter.tryAcquire("k", 2, Duration.ofSeconds(10)));
        assertFalse(limiter.tryAcquire("k", 2, Duration.ofSeconds(10)));
    }

    @Test
    void keysAreIndependent() {
        assertTrue(limiter.tryAcquire("a", 1, MINUTE));
        assertFalse(limiter.tryAcquire("a", 1, MINUTE));

        assertTrue(limiter.tryAcquire("b", 1, MINUTE));
    }

    @Test
    void checkingDoesNotCount() {
        limiter.tryAcquire("k", 2, MINUTE);
        assertFalse(limiter.isExceeded("k", 2, MINUTE));

        limiter.tryAcquire("k", 2, MINUTE);
        assertTrue(limiter.isExceeded("k", 2, MINUTE));
        assertTrue(limiter.isExceeded("k", 2, MINUTE));
    }

    @Test
    void resetForgetsTheKey() {
        limiter.tryAcquire("k", 1, MINUTE);
        assertTrue(limiter.isExceeded("k", 1, MINUTE));

        limiter.reset("k");

        assertFalse(limiter.isExceeded("k", 1, MINUTE));
        assertTrue(limiter.tryAcquire("k", 1, MINUTE));
    }

    @Test
    void idleKeysAreEvicted() {
        limiter.tryAcquire("k", 1, MINUTE);
        assertEquals(1, limiter.trackedKeys());

        now += Duration.ofHours(2).toMillis();
        limiter.evictStale();

        assertEquals(0, limiter.trackedKeys());
    }

    @Test
    void aKeyInsideTheWindowIsNotEvicted() {
        limiter.tryAcquire("k", 1, Duration.ofHours(1));

        now += Duration.ofMinutes(10).toMillis();
        limiter.evictStale();

        assertEquals(1, limiter.trackedKeys());
    }
}