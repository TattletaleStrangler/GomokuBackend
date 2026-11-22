package com.oldmermaidman.gomoku.backend.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Finishes games whose clock ran out.
 *
 * <p>Without this a game would hang forever when both players go silent: the clocks
 * are charged on requests, and there is no request when nobody is there.
 *
 * <p>{@code app.game.timeout-sweeper-enabled=false} switches it off; tests do that to
 * drive timeouts explicitly instead of racing with the background job.
 */
@Component
@ConditionalOnProperty(
        name = "app.game.timeout-sweeper-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class TimeoutSweeper {

    private static final Logger log = LoggerFactory.getLogger(TimeoutSweeper.class);

    private final GameService games;

    public TimeoutSweeper(GameService games) {
        this.games = games;
    }

    @Scheduled(fixedDelay = 1_000)
    public void finishFlaggedGames() {
        int finished = games.expireFlagged();
        // The same tick closes undo requests nobody answered.
        int refused = games.expireUndoRequests();
        if (finished > 0 || refused > 0) {
            log.info("Sweeper: {} game(s) finished on timeout, {} undo request(s) refused",
                    finished, refused);
        }
    }
}