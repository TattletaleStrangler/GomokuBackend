package com.oldmermaidman.gomoku.backend.common;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.LongSupplier;

/**
 * A small sliding-window limiter used to slow down brute force and floods.
 *
 * <p>Counters live in this process only: with several instances each one would count
 * its own share, which is a known limitation of the MVP (TZ_BACKEND.md §15). Counters
 * that have been idle are dropped, so the map cannot grow without bound.
 */
@Component
public class RateLimiter {

    /** Longer than any window the application uses. */
    private static final long IDLE_TTL_MS = Duration.ofMinutes(30).toMillis();

    private final Map<String, ConcurrentLinkedDeque<Long>> hits = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public RateLimiter() {
        this(System::currentTimeMillis);
    }

    /** For tests: a limiter that reads the current time from {@code clock}. */
    RateLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Counts one hit and answers whether it is still within the limit.
     *
     * @return false when the limit is already reached — the hit is not counted then
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        ConcurrentLinkedDeque<Long> timestamps =
                hits.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());
        long now = clock.getAsLong();

        synchronized (timestamps) {
            dropExpired(timestamps, now, window);
            if (timestamps.size() >= limit) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    /** Answers whether the key has already used up its limit; does not count a hit. */
    public boolean isExceeded(String key, int limit, Duration window) {
        ConcurrentLinkedDeque<Long> timestamps = hits.get(key);
        if (timestamps == null) {
            return false;
        }
        long now = clock.getAsLong();

        synchronized (timestamps) {
            dropExpired(timestamps, now, window);
            return timestamps.size() >= limit;
        }
    }

    /** Forgets a key, for example after a successful login. */
    public void reset(String key) {
        hits.remove(key);
    }

    /** How many keys are tracked; used by tests to verify the eviction. */
    int trackedKeys() {
        return hits.size();
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictStale() {
        long now = clock.getAsLong();
        hits.entrySet().removeIf(entry -> {
            Long last = entry.getValue().peekLast();
            return last == null || last < now - IDLE_TTL_MS;
        });
    }

    private static void dropExpired(ConcurrentLinkedDeque<Long> timestamps, long now, Duration window) {
        long cutoff = now - window.toMillis();
        Long first = timestamps.peekFirst();
        while (first != null && first <= cutoff) {
            timestamps.pollFirst();
            first = timestamps.peekFirst();
        }
    }
}