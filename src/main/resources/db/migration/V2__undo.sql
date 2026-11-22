-- V2: undo requests in online games (FEATURES.md, "Отмена хода в онлайн-партии").
--
-- A request is a short-lived state on the game: who asked, until when the opponent
-- must answer, and which move would be taken back. The clocks are frozen while it is
-- pending, so `turn_started_at` is null for the duration.
--
-- Attempts are counted explicitly, not derived from `moves`: an accepted undo deletes
-- the move row, and the attempt must still count.

ALTER TABLE games
    ADD COLUMN undo_requested_by uuid,
    ADD COLUMN undo_deadline timestamptz,
    ADD COLUMN undo_ply integer,
    ADD COLUMN undo_count_black integer NOT NULL DEFAULT 0,
    ADD COLUMN undo_count_white integer NOT NULL DEFAULT 0,
    -- The removed stone, kept so the requester can see what was taken back. Sent only
    -- to the player whose stone it was.
    ADD COLUMN undo_ghost_ply integer,
    ADD COLUMN undo_ghost_x smallint,
    ADD COLUMN undo_ghost_y smallint,
    ADD COLUMN undo_ghost_color varchar(5),
    ADD CONSTRAINT ck_games_undo_count CHECK (undo_count_black >= 0 AND undo_count_white >= 0),
    ADD CONSTRAINT ck_games_undo_ghost_color CHECK (undo_ghost_color IN ('BLACK', 'WHITE'));

-- One request per move: the flag stays after a refusal, so the same move cannot be
-- asked for twice.
ALTER TABLE moves
    ADD COLUMN undo_requested boolean NOT NULL DEFAULT false;