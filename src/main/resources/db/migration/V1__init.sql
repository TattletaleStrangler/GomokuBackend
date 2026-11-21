-- V1: initial schema (see TZ_BACKEND.md §6).
--
-- The schema is owned by Flyway: Hibernate runs with ddl-auto=validate and may never
-- change it. Enum-like columns are varchar with CHECK constraints so that a bad value
-- fails at the database, not only in the service.

CREATE TABLE users (
    id            uuid         PRIMARY KEY,
    nickname      varchar(24)  NOT NULL,
    password_hash varchar(100) NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    -- Filled instead of deleting the row: games of the opponent stay readable.
    deleted_at    timestamptz,
    CONSTRAINT ck_users_nickname_len CHECK (char_length(nickname) BETWEEN 3 AND 24)
);

-- Nicknames are unique case-insensitively; an anonymised account gets a new one.
CREATE UNIQUE INDEX ux_users_nickname ON users (lower(nickname));

CREATE TABLE refresh_tokens (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Only a SHA-256 hash is stored: the token itself is never kept.
    token_hash  varchar(64) NOT NULL,
    issued_at   timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    -- Set when the token is rotated, so a replayed token can be traced.
    replaced_by uuid
);

CREATE UNIQUE INDEX ux_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE games (
    id                 uuid        PRIMARY KEY,
    code               varchar(6)  NOT NULL,
    rule               varchar(16) NOT NULL,
    time_control       varchar(16) NOT NULL,
    black_user_id      uuid        NOT NULL REFERENCES users (id),
    -- Null while the room waits for an opponent.
    white_user_id      uuid        REFERENCES users (id),
    status             varchar(16) NOT NULL,
    result             varchar(16) NOT NULL,
    finish_reason      varchar(16) NOT NULL,
    -- Number of the next move, starting at 1.
    ply                integer     NOT NULL,
    black_remaining_ms bigint,
    white_remaining_ms bigint,
    -- When the clocks of the player to move started; null while the game is not running.
    turn_started_at    timestamptz,
    version            bigint      NOT NULL DEFAULT 0,
    created_at         timestamptz NOT NULL DEFAULT now(),
    started_at         timestamptz,
    finished_at        timestamptz,
    CONSTRAINT ck_games_rule CHECK (rule IN ('FREESTYLE', 'RENJU')),
    CONSTRAINT ck_games_time_control CHECK (time_control IN ('CLASSIC', 'RAPID', 'BLITZ', 'UNTIMED')),
    CONSTRAINT ck_games_status CHECK (status IN ('WAITING', 'ACTIVE', 'FINISHED', 'ABANDONED')),
    CONSTRAINT ck_games_result CHECK (result IN ('BLACK_WIN', 'WHITE_WIN', 'DRAW', 'NONE')),
    CONSTRAINT ck_games_finish_reason CHECK (
        finish_reason IN ('FIVE', 'FORBIDDEN', 'TIMEOUT', 'RESIGN', 'ABANDON', 'NONE')
    ),
    CONSTRAINT ck_games_ply CHECK (ply >= 1),
    CONSTRAINT ck_games_clock CHECK (
        (black_remaining_ms IS NULL OR black_remaining_ms >= 0)
        AND (white_remaining_ms IS NULL OR white_remaining_ms >= 0)
    )
);

-- A code identifies at most one open room.
CREATE UNIQUE INDEX ux_games_open_code ON games (code) WHERE status IN ('WAITING', 'ACTIVE');
-- The clock sweeper scans running games in the order their clocks started.
CREATE INDEX ix_games_running ON games (turn_started_at) WHERE status = 'ACTIVE';
CREATE INDEX ix_games_black ON games (black_user_id);
CREATE INDEX ix_games_white ON games (white_user_id);

CREATE TABLE moves (
    game_id            uuid        NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    ply                integer     NOT NULL,
    x                  smallint    NOT NULL,
    y                  smallint    NOT NULL,
    color              varchar(5)  NOT NULL,
    played_at          timestamptz NOT NULL DEFAULT now(),
    -- Remaining time of the player after the move, for history and analysis.
    remaining_ms_after bigint,
    PRIMARY KEY (game_id, ply),
    CONSTRAINT ck_moves_plies CHECK (ply >= 1),
    CONSTRAINT ck_moves_coords CHECK (x BETWEEN 0 AND 14 AND y BETWEEN 0 AND 14),
    CONSTRAINT ck_moves_color CHECK (color IN ('BLACK', 'WHITE'))
);