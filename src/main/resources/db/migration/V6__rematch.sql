-- Rematch (FEATURES.md): a finished game points at the room its players agreed to
-- replay in. The room itself is an ordinary private game; the column only remembers
-- the offer, so both players can find it from the screen of the finished game.

ALTER TABLE games ADD COLUMN rematch_game_id uuid;

COMMENT ON COLUMN games.rematch_game_id IS
    'The rematch room offered from this finished game, or null when none was offered.';