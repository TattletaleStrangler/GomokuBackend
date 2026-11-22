package com.oldmermaidman.gomoku.backend.ws;

import com.oldmermaidman.gomoku.backend.game.dto.GameSnapshot;

/**
 * One message on the socket: the kind of event plus the whole game state.
 *
 * <p>Every event carries the full snapshot, so a client never mixes events with its
 * own guesses: whatever arrives, it can replace its state.
 */
public record GameEventMessage(String type, GameSnapshot game) {
}