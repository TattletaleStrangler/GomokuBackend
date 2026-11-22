package com.oldmermaidman.gomoku.backend.game.dto;

import com.oldmermaidman.gomoku.backend.game.domain.Rule;
import com.oldmermaidman.gomoku.backend.game.domain.TimeControl;
import jakarta.validation.constraints.NotNull;

/** What the creator of a room chooses. */
public record CreateGameRequest(

        @NotNull
        Rule rule,

        @NotNull
        TimeControl timeControl) {
}