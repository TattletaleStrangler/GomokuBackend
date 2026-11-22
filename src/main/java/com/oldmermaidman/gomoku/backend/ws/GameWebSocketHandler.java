package com.oldmermaidman.gomoku.backend.ws;

import com.oldmermaidman.gomoku.backend.auth.AuthenticatedUser;
import com.oldmermaidman.gomoku.backend.game.GameService;
import com.oldmermaidman.gomoku.backend.game.dto.GameSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * The socket endpoint: it only pushes, and answers {@code PING} with {@code PONG}.
 *
 * <p>Moves, resignations and room changes go over REST — this keeps commands
 * idempotent and testable with plain HTTP, while the socket stays a one-way feed.
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final GameSessionRegistry registry;
    private final GameService games;

    public GameWebSocketHandler(GameSessionRegistry registry, GameService games) {
        this.registry = registry;
        this.games = games;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        AuthenticatedUser player = player(session);
        if (player == null) {
            closeQuietly(session);
            return;
        }
        registry.register(player.id(), session);

        // A fresh or reconnected client gets the current game at once.
        sendCurrentState(player);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        AuthenticatedUser player = player(session);
        if (player == null) {
            return;
        }
        if ("PING".equalsIgnoreCase(message.getPayload().trim())) {
            registry.send(player.id(), new GameEventMessage("PONG", null));
        }
        // Anything else is ignored on purpose: commands are REST calls.
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        AuthenticatedUser player = player(session);
        if (player != null) {
            registry.unregister(player.id(), session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        AuthenticatedUser player = player(session);
        if (player != null) {
            registry.unregister(player.id(), session);
        }
        closeQuietly(session);
    }

    private void sendCurrentState(AuthenticatedUser player) {
        try {
            games.openGameFor(player.id()).ifPresent(gameId -> {
                GameSnapshot snapshot = games.snapshotFor(player.id(), gameId);
                registry.send(player.id(), new GameEventMessage(GameEventType.GAME_STATE.name(), snapshot));
            });
        } catch (RuntimeException e) {
            // A game that disappeared between the two calls is not worth an error on
            // the socket: the client simply waits for the next event.
            log.debug("No initial state for {}: {}", player.id(), e.getMessage());
        }
    }

    private static AuthenticatedUser player(WebSocketSession session) {
        return session.getAttributes().get(JwtHandshakeInterceptor.USER_ATTRIBUTE)
                instanceof AuthenticatedUser user ? user : null;
    }

    private static void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception ignored) {
            // Already gone.
        }
    }
}