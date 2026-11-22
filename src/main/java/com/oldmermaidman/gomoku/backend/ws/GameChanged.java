package com.oldmermaidman.gomoku.backend.ws;

import java.util.UUID;

/**
 * Something happened in a game.
 *
 * <p>Published inside the service's transaction and delivered only after it commits
 * (see {@link GameEventBroadcaster}): a client must never be told about a move that
 * was rolled back.
 */
public record GameChanged(UUID gameId, GameEventType type) {
}