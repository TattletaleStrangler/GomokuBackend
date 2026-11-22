package com.oldmermaidman.gomoku.backend.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * The single socket endpoint, {@code /ws/game}.
 *
 * <p>Authentication happens in the handshake ({@link JwtHandshakeInterceptor}) and
 * the HTTP security chain also covers the upgrade request, so an anonymous client is
 * rejected before a session exists.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler handler;
    private final JwtHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(GameWebSocketHandler handler, JwtHandshakeInterceptor handshakeInterceptor) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/game")
                .addInterceptors(handshakeInterceptor)
                // The client is a native app, not a browser: no cookies are involved.
                .setAllowedOrigins("*");
    }
}