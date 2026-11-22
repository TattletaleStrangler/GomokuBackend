package com.oldmermaidman.gomoku.backend.auth;

import java.util.UUID;

/** The authenticated player of a request, taken from the access token. */
public record AuthenticatedUser(UUID id, String nickname) {
}