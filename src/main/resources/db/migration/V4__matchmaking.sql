-- Public rooms and matchmaking (FEATURES.md, priority B1).
--
-- A room is private to its code by default. A public one is listed in the lobby and is
-- what quick match pairs players into. Existing rooms stay private, as they were.

ALTER TABLE games
    ADD COLUMN visibility varchar(8) NOT NULL DEFAULT 'PRIVATE';

ALTER TABLE games
    ADD CONSTRAINT ck_games_visibility CHECK (visibility IN ('PRIVATE', 'PUBLIC'));

-- The lobby and quick match only ever look at public waiting rooms, oldest first.
CREATE INDEX idx_games_lobby
    ON games (rule, time_control, created_at)
    WHERE status = 'WAITING' AND visibility = 'PUBLIC';