package com.oldmermaidman.gomoku.backend.moderation.web;

import com.oldmermaidman.gomoku.backend.auth.AuthenticatedUser;
import com.oldmermaidman.gomoku.backend.moderation.ModerationService;
import com.oldmermaidman.gomoku.backend.moderation.dto.BlockedPlayerView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The player's own block list (FEATURES.md).
 *
 * <p>Blocking happens in a single tap on the game screen, so a mistake has to be
 * reversible: this is where the blocked players are listed and where one is let back.
 * Both calls act only on the caller's own blocks — nobody can unblock for somebody else.
 */
@RestController
@RequestMapping("/api/v1/blocks")
public class BlocksController {

    private final ModerationService moderation;

    public BlocksController(ModerationService moderation) {
        this.moderation = moderation;
    }

    /** Everybody the caller has blocked, newest first. */
    @GetMapping
    public List<BlockedPlayerView> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return moderation.blocks(user.id()).stream()
                .map(blocked -> new BlockedPlayerView(
                        blocked.userId(), blocked.nickname(), blocked.blockedAt()))
                .toList();
    }

    /** Lets one player back; unblocking twice is not an error. */
    @DeleteMapping("/{blockedId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID blockedId) {
        moderation.unblock(user.id(), blockedId);
    }
}