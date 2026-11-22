package com.oldmermaidman.gomoku.backend.ws;

import com.oldmermaidman.gomoku.backend.game.GameService;
import com.oldmermaidman.gomoku.backend.game.dto.GameSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Pushes game changes to the players' sockets.
 *
 * <p>Runs only after the transaction commits, so a rolled-back move is never
 * announced. Each player receives their own snapshot: the {@code you} field differs,
 * and the clock values are read at send time.
 */
@Component
public class GameEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(GameEventBroadcaster.class);

    private final GameSessionRegistry registry;
    private final GameService games;

    public GameEventBroadcaster(GameSessionRegistry registry, GameService games) {
        this.registry = registry;
        this.games = games;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGameChanged(GameChanged event) {
        try {
            broadcast(event);
        } catch (RuntimeException e) {
            // The game is committed; a broken socket must not turn into a server error.
            log.warn("Could not push game {} to the sockets: {}", event.gameId(), e.getMessage());
        }
    }

    private void broadcast(GameChanged event) {
        for (UUID player : games.participantsOf(event.gameId())) {
            if (!registry.isOnline(player)) {
                continue;
            }
            GameSnapshot snapshot = games.snapshotFor(player, event.gameId());
            registry.send(player, new GameEventMessage(typeFor(event.type(), snapshot), snapshot));
        }
    }

    /**
     * The player who just joined gets the state; the one who was waiting gets
     * {@code OPPONENT_JOINED}. Everyone else receives the event as published.
     */
    private static String typeFor(GameEventType type, GameSnapshot snapshot) {
        if (type == GameEventType.OPPONENT_JOINED && "WHITE".equals(snapshot.you())) {
            return GameEventType.GAME_STATE.name();
        }
        return type.name();
    }
}