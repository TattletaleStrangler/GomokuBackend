package com.oldmermaidman.gomoku.backend.game;

import com.oldmermaidman.gomoku.backend.testsupport.GameTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who plays which colour in a room.
 *
 * <p>The creator may fix the colour, or leave it open ({@code ANY}) so the opponent
 * picks when joining — the room is a private agreement, but a matchmaking client can
 * still ask for a colour of its own.
 */
class GameColorChoiceTest extends GameTestSupport {

    @Test
    void theCreatorDefaultsToBlack() throws Exception {
        Player creator = newPlayer();
        Player joiner = newPlayer();
        String code = createGame(creator, "FREESTYLE", "UNTIMED").text("$.code");

        Response joined = joinGame(joiner, code);

        assertThat(joined.status()).isEqualTo(200);
        assertThat(joined.text("$.you")).isEqualTo("WHITE");
        assertThat(joined.text("$.black.nickname")).isEqualTo(creator.nickname());
        assertThat(joined.text("$.turn")).isEqualTo("BLACK");

        Response creatorView = snapshot(creator, joined.text("$.gameId"));
        assertThat(creatorView.text("$.you")).isEqualTo("BLACK");
    }

    @Test
    void theCreatorMayTakeWhiteAndTheJoinerStarts() throws Exception {
        Player creator = newPlayer();
        Player joiner = newPlayer();
        String code = createGame(creator, "FREESTYLE", "UNTIMED", "WHITE").text("$.code");

        Response joined = joinGame(joiner, code);

        assertThat(joined.status()).isEqualTo(200);
        assertThat(joined.text("$.you")).isEqualTo("BLACK");
        assertThat(joined.text("$.black.nickname")).isEqualTo(joiner.nickname());
        assertThat(joined.text("$.white.nickname")).isEqualTo(creator.nickname());
        assertThat(joined.text("$.turn")).isEqualTo("BLACK");

        Response creatorView = snapshot(creator, joined.text("$.gameId"));
        assertThat(creatorView.text("$.you")).isEqualTo("WHITE");
    }

    @Test
    void anOpenRoomLetsTheJoinerPickWhite() throws Exception {
        Player creator = newPlayer();
        Player joiner = newPlayer();
        String code = createGame(creator, "FREESTYLE", "UNTIMED", "ANY").text("$.code");

        Response joined = joinGame(joiner, code, "WHITE");

        assertThat(joined.status()).isEqualTo(200);
        assertThat(joined.text("$.you")).isEqualTo("WHITE");
        assertThat(joined.text("$.turn")).isEqualTo("BLACK");
        assertThat(snapshot(creator, joined.text("$.gameId")).text("$.you")).isEqualTo("BLACK");
    }

    @Test
    void anOpenRoomDefaultsTheJoinerToBlack() throws Exception {
        Player creator = newPlayer();
        Player joiner = newPlayer();
        String code = createGame(creator, "FREESTYLE", "UNTIMED", "ANY").text("$.code");

        Response joined = joinGame(joiner, code);

        assertThat(joined.text("$.you")).isEqualTo("BLACK");
        assertThat(snapshot(creator, joined.text("$.gameId")).text("$.you")).isEqualTo("WHITE");
    }

    @Test
    void theRoomChoiceWinsWhenTheJoinerAsksForAColour() throws Exception {
        Player creator = newPlayer();
        Player joiner = newPlayer();
        String code = createGame(creator, "FREESTYLE", "UNTIMED", "BLACK").text("$.code");

        // The room fixed black for the creator; the joiner's wish is ignored.
        Response joined = joinGame(joiner, code, "BLACK");

        assertThat(joined.text("$.you")).isEqualTo("WHITE");
        assertThat(joined.text("$.turn")).isEqualTo("BLACK");
    }

    @Test
    void anUnknownColourIsRejected() throws Exception {
        Player creator = newPlayer();

        Response created = createGame(creator, "FREESTYLE", "UNTIMED", "GREEN");

        assertThat(created.status()).isEqualTo(400);
    }
}