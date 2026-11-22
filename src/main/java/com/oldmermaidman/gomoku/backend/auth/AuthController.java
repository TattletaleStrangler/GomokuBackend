package com.oldmermaidman.gomoku.backend.auth;

import com.oldmermaidman.gomoku.backend.auth.dto.CredentialsRequest;
import com.oldmermaidman.gomoku.backend.auth.dto.DeleteAccountRequest;
import com.oldmermaidman.gomoku.backend.auth.dto.RefreshTokenRequest;
import com.oldmermaidman.gomoku.backend.auth.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration, login and session endpoints (TZ_BACKEND.md §7).
 *
 * <p>Only register, login and refresh are open; logout and account deletion need a
 * valid access token — see {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody CredentialsRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody CredentialsRequest request) {
        return auth.login(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return auth.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RefreshTokenRequest request) {
        auth.logout(user.id(), request.refreshToken());
    }

    @DeleteMapping("/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody DeleteAccountRequest request) {
        auth.deleteAccount(user.id(), request.password());
    }
}