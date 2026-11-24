-- Elo rating (FEATURES.md, priority B2).
--
-- Quick-play games are ranked: a game carries the change the result produced for
-- each player, so the client can show it even after a reconnect and the settlement
-- stays idempotent. Private rooms are casual and never settle.

ALTER TABLE users ADD COLUMN rating integer NOT NULL DEFAULT 1200;

ALTER TABLE users ADD CONSTRAINT ck_users_rating CHECK (rating >= 0);

ALTER TABLE games ADD COLUMN ranked boolean NOT NULL DEFAULT false;

-- Null until the game is settled; the columns also record that it was.
ALTER TABLE games ADD COLUMN black_rating_delta integer;
ALTER TABLE games ADD COLUMN white_rating_delta integer;

-- Quick match joins a public ranked room; the lobby listing may show casual ones too.
CREATE INDEX idx_games_ranked_lobby
    ON games (rule, time_control, created_at)
    WHERE status = 'WAITING' AND visibility = 'PUBLIC' AND ranked = true;