package com.oldmermaidman.gomoku.backend.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The open socket of every player.
 *
 * <p>One connection per account: a new one replaces the old, which is what a
 * reconnect on another device should do. Sending is serialised per session because a
 * {@link WebSocketSession} is not safe for concurrent writes.
 */
@Component
public class GameSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(GameSessionRegistry.class);

    private final Map<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    public GameSessionRegistry(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void register(UUID userId, WebSocketSession session) {
        WebSocketSession previous = sessions.put(userId, session);
        if (previous != null && !previous.getId().equals(session.getId())) {
            closeQuietly(previous);
        }
    }

    /** Removes the session only if it is still the registered one. */
    public void unregister(UUID userId, WebSocketSession session) {
        sessions.remove(userId, session);
    }

    public boolean isOnline(UUID userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }

    /** Sends a message; a closed or broken session is dropped, never an error. */
    public void send(UUID userId, GameEventMessage message) {
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) {
            return;
        }
        String payload = mapper.writeValueAsString(message);
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (Exception e) {
            log.debug("Dropping a socket that cannot be written to: {}", e.getMessage());
            sessions.remove(userId, session);
            closeQuietly(session);
        }
    }

    private static void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.NORMAL);
        } catch (Exception ignored) {
            // The connection is gone already; nothing to do.
        }
    }
}