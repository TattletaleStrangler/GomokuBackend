package com.oldmermaidman.gomoku.backend.game;

import com.oldmermaidman.gomoku.backend.game.domain.FinishReason;
import com.oldmermaidman.gomoku.backend.game.domain.GameResult;
import com.oldmermaidman.gomoku.backend.game.domain.GameStatus;
import com.oldmermaidman.gomoku.backend.game.domain.Rule;
import com.oldmermaidman.gomoku.backend.game.domain.Stone;
import com.oldmermaidman.gomoku.backend.game.domain.TimeControl;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * A room or a game (table {@code games}).
 *
 * <p>The entity owns the state transitions — join, charge a move, finish — while the
 * clock arithmetic itself lives in {@code ClockCalculator} so it can be tested
 * without a database.
 */
@Entity
@Table(name = "games")
public class GameEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 6, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Rule rule;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_control", nullable = false, length = 16, updatable = false)
    private TimeControl timeControl;

    @Column(name = "black_user_id", nullable = false, updatable = false)
    private UUID blackUserId;

    @Column(name = "white_user_id")
    private UUID whiteUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GameStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GameResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "finish_reason", nullable = false, length = 16)
    private FinishReason finishReason;

    /** Number of the next move, starting at 1. */
    @Column(nullable = false)
    private int ply;

    @Column(name = "black_remaining_ms")
    private Long blackRemainingMs;

    @Column(name = "white_remaining_ms")
    private Long whiteRemainingMs;

    /** When the clocks of the player to move started; null while nobody is to move. */
    @Column(name = "turn_started_at")
    private Instant turnStartedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** For JPA only. */
    protected GameEntity() {
    }

    private GameEntity(
            UUID id, String code, Rule rule, TimeControl timeControl, UUID blackUserId, Instant now) {
        this.id = id;
        this.code = code;
        this.rule = rule;
        this.timeControl = timeControl;
        this.blackUserId = blackUserId;
        this.status = GameStatus.WAITING;
        this.result = GameResult.NONE;
        this.finishReason = FinishReason.NONE;
        this.ply = 1;
        this.createdAt = now;
        if (timeControl.isTimed()) {
            this.blackRemainingMs = timeControl.mainTimeMs();
            this.whiteRemainingMs = timeControl.mainTimeMs();
        }
    }

    /** A room waiting for an opponent; the creator plays black. */
    public static GameEntity waitingRoom(
            String code, Rule rule, TimeControl timeControl, UUID creator, Instant now) {
        return new GameEntity(UUID.randomUUID(), code, rule, timeControl, creator, now);
    }

    // ---- state changes ----------------------------------------------------

    /** The opponent joined: the game starts and the clocks start with black. */
    public void join(UUID opponent, Instant now) {
        this.whiteUserId = opponent;
        this.status = GameStatus.ACTIVE;
        this.startedAt = now;
        this.turnStartedAt = timeControl.isTimed() ? now : null;
    }

    /**
     * Records a move: charges the mover's remaining time and hands the turn over.
     *
     * @param newRemainingMs the mover's time after the move, increment included
     */
    public void chargeMove(Stone mover, long newRemainingMs, Instant now) {
        if (mover == Stone.BLACK) {
            blackRemainingMs = newRemainingMs;
        } else {
            whiteRemainingMs = newRemainingMs;
        }
        ply++;
        turnStartedAt = timeControl.isTimed() ? now : null;
    }

    public void finish(GameResult result, FinishReason reason, Instant now) {
        this.status = GameStatus.FINISHED;
        this.result = result;
        this.finishReason = reason;
        this.finishedAt = now;
        this.turnStartedAt = null;
    }

    /**
     * The player to move ran out of time: their clock is stored as zero (so the
     * snapshot does not show time they no longer have) and the opponent wins.
     */
    public void flag(Stone loser, Instant now) {
        if (loser == Stone.BLACK) {
            blackRemainingMs = 0L;
        } else {
            whiteRemainingMs = 0L;
        }
        finish(
                loser == Stone.BLACK ? GameResult.WHITE_WIN : GameResult.BLACK_WIN,
                FinishReason.TIMEOUT,
                now);
    }

    /** The room or game is closed without a result (a waiting room is left). */
    public void abandon(Instant now) {
        this.status = GameStatus.ABANDONED;
        this.finishedAt = now;
        this.turnStartedAt = null;
    }

    // ---- queries ----------------------------------------------------------

    /** Colour whose turn it is: black on odd ply numbers, white on even ones. */
    public Stone turn() {
        return ply % 2 == 1 ? Stone.BLACK : Stone.WHITE;
    }

    public Stone colourOf(UUID userId) {
        if (blackUserId.equals(userId)) {
            return Stone.BLACK;
        }
        if (userId.equals(whiteUserId)) {
            return Stone.WHITE;
        }
        return null;
    }

    public boolean isParticipant(UUID userId) {
        return blackUserId.equals(userId) || userId.equals(whiteUserId);
    }

    public boolean isOpen() {
        return status == GameStatus.WAITING || status == GameStatus.ACTIVE;
    }

    /** Time left of a colour as stored; null for an untimed game. */
    public Long remainingOf(Stone colour) {
        return colour == Stone.BLACK ? blackRemainingMs : whiteRemainingMs;
    }

    // ---- getters ----------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Rule getRule() {
        return rule;
    }

    public TimeControl getTimeControl() {
        return timeControl;
    }

    public UUID getBlackUserId() {
        return blackUserId;
    }

    public UUID getWhiteUserId() {
        return whiteUserId;
    }

    public GameStatus getStatus() {
        return status;
    }

    public GameResult getResult() {
        return result;
    }

    public FinishReason getFinishReason() {
        return finishReason;
    }

    public int getPly() {
        return ply;
    }

    public Long getBlackRemainingMs() {
        return blackRemainingMs;
    }

    public Long getWhiteRemainingMs() {
        return whiteRemainingMs;
    }

    public Instant getTurnStartedAt() {
        return turnStartedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}