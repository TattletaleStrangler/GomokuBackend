package com.oldmermaidman.gomoku.backend.game;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite key of a move: the game plus the number of the move. */
public class MoveId implements Serializable {

    private UUID gameId;
    private int ply;

    public MoveId() {
    }

    public MoveId(UUID gameId, int ply) {
        this.gameId = gameId;
        this.ply = ply;
    }

    public UUID getGameId() {
        return gameId;
    }

    public int getPly() {
        return ply;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MoveId that)) {
            return false;
        }
        return ply == that.ply && Objects.equals(gameId, that.gameId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameId, ply);
    }
}