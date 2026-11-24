package com.oldmermaidman.gomoku.backend.moderation.web;

import com.oldmermaidman.gomoku.backend.auth.AuthenticatedUser;
import com.oldmermaidman.gomoku.backend.moderation.ModerationService;
import com.oldmermaidman.gomoku.backend.moderation.dto.ReportRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Blocking and reporting the opponent of a game (FUTURE.md, B4 for the review side).
 *
 * <p>Both answer with 204: the client does not need anything back, and there is nothing
 * to show about a complaint.
 */
@RestController
@RequestMapping("/api/v1/games/{id}")
public class ModerationController {

    private final ModerationService moderation;

    public ModerationController(ModerationService moderation) {
        this.moderation = moderation;
    }

    /** "Do not pair me with this player again." */
    @PostMapping("/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        moderation.block(user.id(), id);
    }

    /** A complaint about the opponent; the game is attached for the admin to look at. */
    @PostMapping("/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody ReportRequest request) {
        moderation.report(user.id(), id, request.reason());
    }
}