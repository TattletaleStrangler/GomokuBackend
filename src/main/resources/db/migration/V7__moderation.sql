-- Blocking and reports (FUTURE.md).
--
-- Public quick match brings strangers together: a player can stop meeting somebody
-- again, and can report them. Blocks are used by the lobby and the matchmaking query,
-- reports are only stored — reading them is an admin task (B4).

CREATE TABLE blocks (
    id uuid PRIMARY KEY,
    blocker_id uuid NOT NULL REFERENCES users(id),
    blocked_id uuid NOT NULL REFERENCES users(id),
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_blocks_pair UNIQUE (blocker_id, blocked_id),
    CONSTRAINT ck_blocks_not_self CHECK (blocker_id <> blocked_id)
);

-- The matchmaking query looks for a block in either direction.
CREATE INDEX idx_blocks_blocked ON blocks (blocked_id);

CREATE TABLE reports (
    id uuid PRIMARY KEY,
    reporter_id uuid NOT NULL REFERENCES users(id),
    reported_id uuid NOT NULL REFERENCES users(id),
    game_id uuid,
    reason varchar(16) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_reports_not_self CHECK (reporter_id <> reported_id),
    CONSTRAINT ck_reports_reason CHECK (reason IN ('NICKNAME', 'STALLING', 'CHEATING', 'OTHER'))
);

CREATE INDEX idx_reports_reported ON reports (reported_id);