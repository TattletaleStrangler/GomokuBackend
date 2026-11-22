-- The colour the creator of a room wants to play (FEATURES.md, rooms by code).
--
-- BLACK / WHITE fix both seats; ANY means the creator left the choice to the opponent,
-- who picks a colour when joining. Existing rooms were all created by black, hence the
-- default.

ALTER TABLE games
    ADD COLUMN creator_color varchar(5) NOT NULL DEFAULT 'BLACK';

ALTER TABLE games
    ADD CONSTRAINT ck_games_creator_color CHECK (creator_color IN ('BLACK', 'WHITE', 'ANY'));