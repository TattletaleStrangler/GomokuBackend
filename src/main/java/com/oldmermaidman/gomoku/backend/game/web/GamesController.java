package com.oldmermaidman.gomoku.backend.game.web;

import com.oldmermaidman.gomoku.backend.auth.AuthenticatedUser;
import com.oldmermaidman.gomoku.backend.game.domain.Rule;
import com.oldmermaidman.gomoku.backend.game.domain.TimeControl;
import com.oldmermaidman.gomoku.backend.game.dto.CreateGameRequest;
import com.oldmermaidman.gomoku.backend.game.GameService;
import com.oldmermaidman.gomoku.backend.game.dto.GameHistoryView;
import com.oldmermaidman.gomoku.backend.game.dto.GameSnapshot;
import com.oldmermaidman.gomoku.backend.game.dto.JoinGameRequest;
import com.oldmermaidman.gomoku.backend.game.dto.LobbyGameView;
import com.oldmermaidman.gomoku.backend.game.dto.MoveRequest;
import com.oldmermaidman.gomoku.backend.game.dto.QuickGameRequest;
import com.oldmermaidman.gomoku.backend.game.dto.UndoDecisionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Rooms and games (TZ_BACKEND.md §7).
 *
 * <p>Commands only — the side that receives events is the WebSocket endpoint. Each
 * call answers with the whole game state, so a client can replace its copy instead of
 * patching it.
 */
@RestController
@RequestMapping("/api/v1/games")
public class GamesController {

    private final GameService games;

    public GamesController(GameService games) {
        this.games = games;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GameSnapshot create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateGameRequest request) {
        return games.create(
                user.id(),
                request.rule(),
                request.timeControl(),
                request.color(),
                request.visibility());
    }

    /**
     * Quick match (FEATURES.md, B1): joins the oldest public room with the same rule
     * and time control, or opens one and returns it in {@code WAITING}.
     */
    @PostMapping("/quick")
    public GameSnapshot quickMatch(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody QuickGameRequest request) {
        return games.quickMatch(user.id(), request.rule(), request.timeControl(), request.color());
    }

    /** The open-games list; both filters are optional. */
    @GetMapping("/lobby")
    public List<LobbyGameView> lobby(
            @RequestParam(required = false) Rule rule,
            @RequestParam(required = false) TimeControl timeControl) {
        return games.lobby(rule, timeControl);
    }

    /** The caller's finished games, newest first; the moves come from `/{id}`. */
    @GetMapping("/history")
    public List<GameHistoryView> history(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "20") int limit) {
        return games.history(user.id(), limit);
    }

    @PostMapping("/join")
    public GameSnapshot join(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody JoinGameRequest request) {
        return games.join(user.id(), request.code(), request.color());
    }

    @GetMapping("/{id}")
    public GameSnapshot get(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        return games.get(user.id(), id);
    }

    @PostMapping("/{id}/moves")
    public GameSnapshot move(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody MoveRequest request) {
        return games.move(user.id(), id, request.ply(), request.x(), request.y());
    }

    @PostMapping("/{id}/resign")
    public GameSnapshot resign(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        return games.resign(user.id(), id);
    }

    /** Offers or accepts a rematch of a finished game (FUTURE.md). */
    @PostMapping("/{id}/rematch")
    public GameSnapshot rematch(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        return games.rematch(user.id(), id);
    }

    /** Asks the opponent to take the last move back (FEATURES.md, online undo). */
    @PostMapping("/{id}/undo-request")
    public GameSnapshot requestUndo(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        return games.requestUndo(user.id(), id);
    }

    /** The opponent's answer to a pending request. */
    @PostMapping("/{id}/undo-decision")
    public GameSnapshot decideUndo(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UndoDecisionRequest request) {
        return games.decideUndo(user.id(), id, request.accept());
    }

    @PostMapping("/{id}/leave")
    public GameSnapshot leave(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        return games.leave(user.id(), id);
    }
}