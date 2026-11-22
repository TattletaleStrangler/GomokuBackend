package com.oldmermaidman.gomoku.backend.user;

import com.oldmermaidman.gomoku.backend.auth.AuthService;
import com.oldmermaidman.gomoku.backend.auth.AuthenticatedUser;
import com.oldmermaidman.gomoku.backend.auth.dto.ProfileResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in player's profile and record. */
@RestController
@RequestMapping("/api/v1")
public class ProfileController {

    private final AuthService auth;

    public ProfileController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/me")
    public ProfileResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return auth.profile(user.id());
    }
}