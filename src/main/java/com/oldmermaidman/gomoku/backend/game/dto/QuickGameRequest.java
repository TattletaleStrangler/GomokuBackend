package com.oldmermaidman.gomoku.backend.game.dto;

import com.oldmermaidman.gomoku.backend.game.domain.ColorChoice;
import com.oldmermaidman.gomoku.backend.game.domain.Rule;
import com.oldmermaidman.gomoku.backend.game.domain.TimeControl;
import jakarta.validation.constraints.NotNull;

/**
 * Quick match: the settings the player wants (FEATURES.md, priority B1).
 *
 * <p>{@code color} is optional and means "no preference": the room is opened with
 * {@code ANY}, and when a room is joined the usual colour rules apply — a creator who
 * fixed a colour decides the seats.
 */
public record QuickGameRequest(

        @NotNull
        Rule rule,

        @NotNull
        TimeControl timeControl,

        ColorChoice color) {
}