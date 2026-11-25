package com.oldmermaidman.gomoku.backend.ws;

import com.oldmermaidman.gomoku.backend.auth.AuthenticatedUser;
import com.oldmermaidman.gomoku.backend.auth.JwtService;
import com.oldmermaidman.gomoku.backend.common.ApiException;
import com.oldmermaidman.gomoku.backend.common.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.time.Duration;
import java.util.Map;

/**
 * Authenticates the WebSocket handshake with the same Bearer token as the REST API.
 *
 * <p>A header is used rather than a query parameter on purpose: tokens in URLs end up
 * in access logs. OkHttp, the client's stack, can set handshake headers.
 *
 * <p>The handshake is also rate-limited per account (security audit, P1): each one
 * creates a server-side session, and one account may hold only one socket, so a client
 * that connects in a loop would otherwise make the server do work for nothing. The
 * client reconnects with a backoff of at most every 30 seconds, so the limit is far
 * above normal use.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    /** Session attribute holding the player. */
    static final String USER_ATTRIBUTE = "player";

    private static final String BEARER_PREFIX = "Bearer ";

    /** A reconnecting client stays well below this. */
    private static final int HANDSHAKE_LIMIT = 30;

    private static final Duration HANDSHAKE_WINDOW = Duration.ofMinutes(1);

    private final JwtService jwtService;
    private final RateLimiter limiter;

    public JwtHandshakeInterceptor(JwtService jwtService, RateLimiter limiter) {
        this.jwtService = jwtService;
        this.limiter = limiter;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Map<String, Object> attributes) {

        if (request instanceof ServletServerHttpRequest servletRequest) {
            String header = servletRequest.getServletRequest().getHeader("Authorization");
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                try {
                    AuthenticatedUser user = jwtService.verify(header.substring(BEARER_PREFIX.length()).trim());
                    if (!limiter.tryAcquire(
                            "ws-handshake:" + user.id(), HANDSHAKE_LIMIT, HANDSHAKE_WINDOW)) {
                        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return false;
                    }
                    attributes.put(USER_ATTRIBUTE, user);
                    return true;
                } catch (ApiException e) {
                    // Fall through to the rejection below.
                }
            }
        }

        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Exception exception) {
        // Nothing to do.
    }
}