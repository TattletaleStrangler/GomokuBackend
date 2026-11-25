-- Draw offers (FUTURE.md): the same request/answer shape as an undo, except that an
-- accepted offer ends the game with a draw. Refusals and timeouts cost an attempt, so
-- the offer cannot be used to pester the opponent.

ALTER TABLE games ADD COLUMN draw_requested_by uuid;

ALTER TABLE games ADD COLUMN draw_deadline timestamptz;

ALTER TABLE games ADD COLUMN draw_count_black integer NOT NULL DEFAULT 0;

ALTER TABLE games ADD COLUMN draw_count_white integer NOT NULL DEFAULT 0;

COMMENT ON COLUMN games.draw_requested_by IS
    'Who offered a draw; null when no offer is pending.';

-- A draw agreed by both players gets its own reason, so the history can tell it apart
-- from a draw that ended some other way.
ALTER TABLE games DROP CONSTRAINT ck_games_finish_reason;

ALTER TABLE games ADD CONSTRAINT ck_games_finish_reason CHECK (
    finish_reason IN ('FIVE', 'FORBIDDEN', 'TIMEOUT', 'RESIGN', 'ABANDON', 'AGREE', 'NONE')
);