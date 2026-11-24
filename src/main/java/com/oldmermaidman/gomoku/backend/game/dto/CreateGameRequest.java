package com.oldmermaidman.gomoku.backend.game.dto;

import com.oldmermaidman.gomoku.backend.game.domain.ColorChoice;
import com.oldmermaidman.gomoku.backend.game.domain.Rule;
import com.oldmermaidman.gomoku.backend.game.domain.TimeControl;
import com.oldmermaidman.gomoku.backend.game.domain.Visibility;
import jakarta.validation.constraints.NotNull;

/**
 * What the creator of a room chooses.
 *
 * <p>{@code color} may be omitted: then the creator plays black. {@code ANY} means the
 * creator leaves the colour open for the opponent who joins. {@code visibility} may be
 * omitted too: then the room is private to its code and never listed.
 */
public record CreateGameRequest(

        @NotNull
        Rule rule,

        @NotNull
        TimeControl timeControl,

        ColorChoice color,

        Visibility visibility) {
}