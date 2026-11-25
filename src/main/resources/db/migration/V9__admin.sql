-- Administration (FEATURES.md, B4): who may review reports, what was decided, who is
-- banned from ranked play, and where server errors and client crashes end up.

ALTER TABLE users ADD COLUMN role varchar(8) NOT NULL DEFAULT 'PLAYER';

ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('PLAYER', 'ADMIN'));

-- A queue, not a table dump: every report has a state and a note from the admin.
ALTER TABLE reports ADD COLUMN status varchar(10) NOT NULL DEFAULT 'NEW';

ALTER TABLE reports ADD CONSTRAINT ck_reports_status
    CHECK (status IN ('NEW', 'REVIEWED', 'ACTIONED', 'DISMISSED'));

ALTER TABLE reports ADD COLUMN reviewed_by uuid;

ALTER TABLE reports ADD COLUMN reviewed_at timestamptz;

ALTER TABLE reports ADD COLUMN note varchar(200);

-- One complaint per game per player: the daily cap alone let one player report the
-- same game twenty times.
CREATE UNIQUE INDEX uq_reports_game_reporter ON reports (reporter_id, game_id);

-- A ban is a row, not a flag on the account: the history of who was banned when and
-- why stays readable after the ban expires or is lifted.
CREATE TABLE bans (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    until timestamptz NOT NULL,
    kind varchar(8) NOT NULL,
    reason varchar(200) NOT NULL,
    issued_by uuid REFERENCES users(id),
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_bans_kind CHECK (kind IN ('RANKED', 'ACCOUNT'))
);

CREATE INDEX idx_bans_user ON bans (user_id);

-- Every decision an admin makes, so "why is this account banned" has an answer.
CREATE TABLE admin_actions (
    id uuid PRIMARY KEY,
    admin_id uuid NOT NULL REFERENCES users(id),
    target_user_id uuid REFERENCES users(id),
    report_id uuid REFERENCES reports(id),
    action varchar(16) NOT NULL,
    note varchar(200),
    created_at timestamptz NOT NULL
);

CREATE INDEX idx_admin_actions_target ON admin_actions (target_user_id, created_at DESC);

-- Server errors: 5xx only, with the request id that is also returned to the client, so
-- a player's screenshot can be traced to a stack trace.
CREATE TABLE error_events (
    id uuid PRIMARY KEY,
    request_id varchar(36),
    occurred_at timestamptz NOT NULL,
    status integer NOT NULL,
    method varchar(8),
    path varchar(200),
    user_id uuid,
    error_code varchar(40),
    message varchar(500),
    stacktrace text
);

CREATE INDEX idx_error_events_time ON error_events (occurred_at DESC);

-- Client crashes, sent on the next launch. The fingerprint groups equal crashes; the
-- install id counts how many devices hit one.
CREATE TABLE crash_reports (
    id uuid PRIMARY KEY,
    fingerprint varchar(64) NOT NULL,
    install_id varchar(64),
    user_id uuid REFERENCES users(id),
    app_version varchar(16),
    version_code integer,
    device varchar(80),
    android varchar(40),
    locale varchar(16),
    thread varchar(80),
    exception varchar(160),
    message varchar(500),
    stacktrace text,
    created_at timestamptz NOT NULL
);

CREATE INDEX idx_crash_fingerprint ON crash_reports (fingerprint, created_at DESC);