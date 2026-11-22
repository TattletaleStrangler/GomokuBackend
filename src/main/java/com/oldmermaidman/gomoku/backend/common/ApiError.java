package com.oldmermaidman.gomoku.backend.common;

import org.springframework.http.HttpStatus;

/**
 * Error codes of the API (TZ_BACKEND.md §7).
 *
 * <p>The code — not the message — is the contract: the Android client translates it
 * into the user's language, so messages here are for humans and logs only.
 */
public enum ApiError {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    NICKNAME_TAKEN(HttpStatus.CONFLICT),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    ACCOUNT_DELETED(HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    NOT_A_PARTICIPANT(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    GAME_NOT_FOUND(HttpStatus.NOT_FOUND),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND),
    ROOM_FULL(HttpStatus.CONFLICT),
    SELF_JOIN(HttpStatus.CONFLICT),
    ACTIVE_GAME_EXISTS(HttpStatus.CONFLICT),
    GAME_FINISHED(HttpStatus.CONFLICT),
    NOT_YOUR_TURN(HttpStatus.CONFLICT),
    WRONG_PLY(HttpStatus.CONFLICT),
    CELL_OCCUPIED(HttpStatus.CONFLICT),
    FORBIDDEN_MOVE(HttpStatus.CONFLICT),
    OUT_OF_BOARD(HttpStatus.BAD_REQUEST),
    UNDO_NOT_ALLOWED(HttpStatus.CONFLICT),
    UNDO_TOO_LATE(HttpStatus.CONFLICT),
    UNDO_LIMIT_REACHED(HttpStatus.CONFLICT),
    UNDO_ALREADY_REQUESTED(HttpStatus.CONFLICT),
    UNDO_NOT_REQUESTED(HttpStatus.CONFLICT),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ApiError(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}