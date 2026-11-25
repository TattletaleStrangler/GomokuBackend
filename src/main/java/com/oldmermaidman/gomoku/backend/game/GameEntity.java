package com.oldmermaidman.gomoku.backend.game;

import com.oldmermaidman.gomoku.backend.game.domain.ColorChoice;
import com.oldmermaidman.gomoku.backend.game.domain.FinishReason;
import com.oldmermaidman.gomoku.backend.game.domain.GameResult;
import com.oldmermaidman.gomoku.backend.game.domain.GameStatus;
import com.oldmermaidman.gomoku.backend.game.domain.Rule;
import com.oldmermaidman.gomoku.backend.game.domain.Stone;
import com.oldmermaidman.gomoku.backend.game.domain.TimeControl;
import com.oldmermaidman.gomoku.backend.game.domain.Visibility;
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

    @Column(name = "black_user_id", nullable = false)
    private UUID blackUserId;

    @Column(name = "white_user_id")
    private UUID whiteUserId;

    /** The colour the creator asked for; {@code ANY} lets the joiner choose. */
    @Enumerated(EnumType.STRING)
    @Column(name = "creator_color", nullable = false, length = 5)
    private ColorChoice creatorColor;

    /** Private rooms are shared by code; public ones are listed and matchmade. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Visibility visibility;

    /** Quick-play games settle rating; rooms by code are casual. */
    @Column(nullable = false)
    private boolean ranked;

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

    /** Who asked to take the last move back; null when no request is pending. */
    @Column(name = "undo_requested_by")
    private UUID undoRequestedBy;

    /** The moment the opponent's answer stops counting. */
    @Column(name = "undo_deadline")
    private Instant undoDeadline;

    /** The move the request is about. */
    @Column(name = "undo_ply")
    private Integer undoPly;

    @Column(name = "undo_count_black", nullable = false)
    private int undoCountBlack;

    @Column(name = "undo_count_white", nullable = false)
    private int undoCountWhite;

    /** The stone an accepted undo took back, kept for the requester's ghost. */
    @Column(name = "undo_ghost_ply")
    private Integer undoGhostPly;

    @Column(name = "undo_ghost_x")
    private Short undoGhostX;

    @Column(name = "undo_ghost_y")
    private Short undoGhostY;

    @Enumerated(EnumType.STRING)
    @Column(name = "undo_ghost_color", length = 5)
    private Stone undoGhostColor;

    /**
     * The rating change the result produced for each player; null until a ranked game
     * is settled. The columns double as the "already settled" flag.
     */
    @Column(name = "black_rating_delta")
    private Integer blackRatingDelta;

    @Column(name = "white_rating_delta")
    private Integer whiteRatingDelta;

    /** The room offered from this finished game, or null when none was offered. */
    @Column(name = "rematch_game_id")
    private UUID rematchGameId;

    /** Who offered a draw; null when no offer is pending. */
    @Column(name = "draw_requested_by")
    private UUID drawRequestedBy;

    /** The moment the opponent's answer to the draw offer stops counting. */
    @Column(name = "draw_deadline")
    private Instant drawDeadline;

    @Column(name = "draw_count_black", nullable = false)
    private int drawCountBlack;

    @Column(name = "draw_count_white", nullable = false)
    private int drawCountWhite;

    /** For JPA only. */
    protected GameEntity() {
    }

    private GameEntity(
            UUID id,
            String code,
            Rule rule,
            TimeControl timeControl,
            UUID blackUserId,
            ColorChoice creatorColor,
            Visibility visibility,
            boolean ranked,
            Instant now) {
        this.id = id;
        this.code = code;
        this.rule = rule;
        this.timeControl = timeControl;
        this.blackUserId = blackUserId;
        this.creatorColor = creatorColor;
        this.visibility = visibility;
        this.ranked = ranked;
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

    /**
     * A room waiting for an opponent. The creator is stored in the black seat until the
     * opponent joins; [ColorChoice] decides the final seating then.
     */
    public static GameEntity waitingRoom(
            String code,
            Rule rule,
            TimeControl timeControl,
            UUID creator,
            ColorChoice creatorColor,
            Visibility visibility,
            boolean ranked,
            Instant now) {
        return new GameEntity(
                UUID.randomUUID(), code, rule, timeControl, creator, creatorColor, visibility, ranked, now);
    }

    // ---- state changes ----------------------------------------------------

    /**
     * The opponent joined: the game starts, the seats are fixed and the clocks start
     * with black.
     *
     * <p>When the creator left the colour open ({@link ColorChoice#ANY}), [joinerColor]
     * decides the seating; otherwise the creator's choice wins and the joiner's wish is
     * ignored, because the room already fixed both seats.
     */
    public void join(UUID opponent, ColorChoice joinerColor, Instant now) {
        UUID creator = blackUserId;
        boolean creatorPlaysBlack = switch (creatorColor) {
            case BLACK -> true;
            case WHITE -> false;
            // The joiner picks: a white wish means the creator takes black.
            case ANY -> joinerColor == ColorChoice.WHITE;
        };

        if (creatorPlaysBlack) {
            this.blackUserId = creator;
            this.whiteUserId = opponent;
        } else {
            this.blackUserId = opponent;
            this.whiteUserId = creator;
        }

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
        clearGhostFor(mover);
        ply++;
        turnStartedAt = timeControl.isTimed() ? now : null;
    }

    public void finish(GameResult result, FinishReason reason, Instant now) {
        this.status = GameStatus.FINISHED;
        this.result = result;
        this.finishReason = reason;
        this.finishedAt = now;
        this.turnStartedAt = null;
        // A request is moot once the game is over.
        clearUndoRequest();
        clearDrawRequest();
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

    // ---- undo requests ----------------------------------------------------

    /** Registers a request and counts it: a refusal costs an attempt too. */
    public void requestUndo(UUID requester, int ply, Instant deadline) {
        this.undoRequestedBy = requester;
        this.undoPly = ply;
        this.undoDeadline = deadline;
        if (requester.equals(blackUserId)) {
            undoCountBlack++;
        } else {
            undoCountWhite++;
        }
    }

    public void clearUndoRequest() {
        this.undoRequestedBy = null;
        this.undoPly = null;
        this.undoDeadline = null;
    }

    /** Freezes the clock of the player to move at the value it has right now. */
    public void freezeClock(Stone player, long remainingNow) {
        setRemaining(player, remainingNow);
        this.turnStartedAt = null;
    }

    /** Restarts the frozen clock of the player to move. */
    public void resumeClock(Instant now) {
        this.turnStartedAt = timeControl.isTimed() ? now : null;
    }

    /**
     * Takes the last move back: the stone is gone, the turn returns to its owner, and
     * the cell is remembered so that player can see where the stone was.
     *
     * @param remainingAfterIncrementRemoved the mover's time without the increment the
     *                                       move had earned, or null for an untimed game
     */
    public void acceptUndo(
            Stone mover,
            int ghostPly,
            int ghostX,
            int ghostY,
            Long remainingAfterIncrementRemoved,
            Instant now) {
        if (remainingAfterIncrementRemoved != null) {
            setRemaining(mover, remainingAfterIncrementRemoved);
        }
        this.undoGhostPly = ghostPly;
        this.undoGhostX = (short) ghostX;
        this.undoGhostY = (short) ghostY;
        this.undoGhostColor = mover;
        this.ply = ghostPly;
        clearUndoRequest();
        this.turnStartedAt = timeControl.isTimed() ? now : null;
    }

    public boolean hasPendingUndo() {
        return undoRequestedBy != null;
    }

    public int undosUsedBy(Stone player) {
        return player == Stone.BLACK ? undoCountBlack : undoCountWhite;
    }

    // ---- draw offers ------------------------------------------------------

    /** Registers a draw offer and counts it: a refusal costs an attempt too. */
    public void requestDraw(UUID requester, Instant deadline) {
        this.drawRequestedBy = requester;
        this.drawDeadline = deadline;
        if (requester.equals(blackUserId)) {
            drawCountBlack++;
        } else {
            drawCountWhite++;
        }
    }

    public void clearDrawRequest() {
        this.drawRequestedBy = null;
        this.drawDeadline = null;
    }

    public boolean hasPendingDraw() {
        return drawRequestedBy != null;
    }

    public int drawsUsedBy(Stone player) {
        return player == Stone.BLACK ? drawCountBlack : drawCountWhite;
    }

    private void setRemaining(Stone player, long remaining) {
        if (player == Stone.BLACK) {
            blackRemainingMs = remaining;
        } else {
            whiteRemainingMs = remaining;
        }
    }

    /** The ghost disappears once its owner plays again. */
    private void clearGhostFor(Stone mover) {
        if (undoGhostColor == mover) {
            this.undoGhostPly = null;
            this.undoGhostX = null;
            this.undoGhostY = null;
            this.undoGhostColor = null;
        }
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

    public ColorChoice getCreatorColor() {
        return creatorColor;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public boolean isRanked() {
        return ranked;
    }

    public Integer getBlackRatingDelta() {
        return blackRatingDelta;
    }

    public Integer getWhiteRatingDelta() {
        return whiteRatingDelta;
    }

    /**
     * Records what the result changed for each player. Called once per ranked game;
     * the stored values keep the settlement idempotent and survive a reconnect.
     */
    public void settleRating(int blackDelta, int whiteDelta) {
        this.blackRatingDelta = blackDelta;
        this.whiteRatingDelta = whiteDelta;
    }

    public UUID getRematchGameId() {
        return rematchGameId;
    }

    /** Points this finished game at the room offered for a rematch. */
    public void offerRematch(UUID rematchId) {
        this.rematchGameId = rematchId;
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

    public UUID getUndoRequestedBy() {
        return undoRequestedBy;
    }

    public Instant getUndoDeadline() {
        return undoDeadline;
    }

    public UUID getDrawRequestedBy() {
        return drawRequestedBy;
    }

    public Instant getDrawDeadline() {
        return drawDeadline;
    }

    public Integer getUndoPly() {
        return undoPly;
    }

    public Integer getUndoGhostPly() {
        return undoGhostPly;
    }

    public Short getUndoGhostX() {
        return undoGhostX;
    }

    public Short getUndoGhostY() {
        return undoGhostY;
    }

    public Stone getUndoGhostColor() {
        return undoGhostColor;
    }
}