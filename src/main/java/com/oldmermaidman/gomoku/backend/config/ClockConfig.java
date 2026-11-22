package com.oldmermaidman.gomoku.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The clock the game logic reads time from.
 *
 * <p>Injected everywhere instead of {@code Instant.now()} so tests can move time
 * forward and see a timeout without waiting for it.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}