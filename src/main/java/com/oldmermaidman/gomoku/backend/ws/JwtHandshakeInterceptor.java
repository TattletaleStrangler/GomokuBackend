package com.oldmermaidman.gomoku.backend.ws;

import com.oldmermaidman.gomoku.backend.auth.AuthenticatedUser;
import com.oldmermaidman.gomoku.backend.auth.JwtService;
import com.oldmermaidman.gomoku.backend.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Authenticates the WebSocket handshake with the same Bearer token as the REST API.
 *
 * <p>A header is used rather than a query parameter on purpose: tokens in URLs end up
 * in access logs. OkHttp, the client's stack, can set handshake headers.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    /** Session attribute holding the player. */
    static final String USER_ATTRIBUTE = "player";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
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