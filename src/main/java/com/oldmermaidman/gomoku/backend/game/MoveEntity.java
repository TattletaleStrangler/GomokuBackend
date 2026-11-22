package com.oldmermaidman.gomoku.backend.game;

import com.oldmermaidman.gomoku.backend.game.domain.Stone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One move of a game (table {@code moves}).
 *
 * <p>The pair {@code (game_id, ply)} is the primary key: it also makes a repeated
 * request idempotent at the database level — a duplicate ply cannot be inserted.
 */
@Entity
@Table(name = "moves")
@IdClass(MoveId.class)
public class MoveEntity {

    @Id
    @Column(name = "game_id", nullable = false, updatable = false)
    private UUID gameId;

    @Id
    @Column(name = "ply", nullable = false, updatable = false)
    private int ply;

    @Column(nullable = false)
    private short x;

    @Column(nullable = false)
    private short y;

    @Enumerated(EnumType.STRING)
    @Column(name = "color", nullable = false, length = 5)
    private Stone color;

    @Column(name = "played_at", nullable = false, updatable = false)
    private Instant playedAt;

    /** Time left of the player after the move; null for an untimed game. */
    @Column(name = "remaining_ms_after")
    private Long remainingMsAfter;

    /** For JPA only. */
    protected MoveEntity() {
    }

    public MoveEntity(UUID gameId, int ply, int x, int y, Stone color, Instant playedAt, Long remainingMsAfter) {
        this.gameId = gameId;
        this.ply = ply;
        this.x = (short) x;
        this.y = (short) y;
        this.color = color;
        this.playedAt = playedAt;
        this.remainingMsAfter = remainingMsAfter;
    }

    public UUID getGameId() {
        return gameId;
    }

    public int getPly() {
        return ply;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Stone getColor() {
        return color;
    }

    public Instant getPlayedAt() {
        return playedAt;
    }

    public Long getRemainingMsAfter() {
        return remainingMsAfter;
    }
}