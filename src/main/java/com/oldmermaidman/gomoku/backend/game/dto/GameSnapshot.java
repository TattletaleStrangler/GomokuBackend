package com.oldmermaidman.gomoku.backend.game.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The whole state of a game, as every game endpoint returns it
 * (TZ_BACKEND.md §7).
 *
 * <p>{@code you} is the caller's colour, {@code turn} is null when nobody is to move,
 * and the clock values are already the live ones at {@code serverTime}: the client
 * counts down from them instead of trusting the device clock.
 */
public record GameSnapshot(
        UUID gameId,
        String code,
        String rule,
        String timeControl,
        String status,
        /** True for quick-play games, whose result settles the Elo rating. */
        boolean ranked,
        PlayerView black,
        PlayerView white,
        String you,
        int ply,
        String turn,
        List<MoveView> moves,
        ClockView clocks,
        Instant serverTime,
        String result,
        String finishReason,
        /** How many undo requests the viewer still has in this game. */
        int undoRemaining,
        /** The pending undo request, or null when none is pending. */
        UndoView undo,
        /**
         * The stone an accepted undo removed, shown as a ghost only to the player who
         * asked; null for the opponent and when nothing was taken back.
         */
        MoveView lastUndoneMove) {

    /**
     * One player: {@code rating} is their current Elo, {@code ratingDelta} the change a
     * just-finished ranked game produced (null in every other case).
     */
    public record PlayerView(UUID userId, String nickname, Integer rating, Integer ratingDelta) {
    }

    public record MoveView(int ply, int x, int y, String color) {
    }

    /** Milliseconds left; both null for a game without clocks. */
    public record ClockView(Long blackRemainingMs, Long whiteRemainingMs, String running) {
    }

    /**
     * A pending undo request: who asked (as a colour), until when the opponent must
     * answer (server time), and which move is at stake.
     */
    public record UndoView(String requestedBy, Instant deadline, int ply) {
    }
}