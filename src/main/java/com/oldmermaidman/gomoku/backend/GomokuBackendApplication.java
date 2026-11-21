package com.oldmermaidman.gomoku.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the Gomoku backend.
 *
 * <p>The server is authoritative for everything about a game: rules, turn order,
 * clocks and results. Clients send intents (REST) and receive events (WebSocket);
 * see {@code ../TZ_BACKEND.md} for the contract.
 */
@SpringBootApplication
@EnableScheduling
public class GomokuBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(GomokuBackendApplication.class, args);
    }
}