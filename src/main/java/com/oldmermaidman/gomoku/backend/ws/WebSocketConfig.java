package com.oldmermaidman.gomoku.backend.ws;

import jakarta.websocket.server.ServerContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
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

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

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

    /**
     * Bounds what a socket can cost (security audit, P1).
     *
     * <p>The client sends an application-level {@code PING} every ten seconds, so two
     * minutes of silence means the connection is dead and the container may close it
     * without waiting for the OS to notice. The message buffer is far above anything the
     * client sends (a snapshot of a 15×15 game) and far below a memory problem. One
     * socket per account is already enforced by {@link GameSessionRegistry}.
     *
     * <p>The limits are applied to the servlet context rather than through
     * {@code ServletServerContainerFactoryBean}: that factory needs a real container and
     * fails in a mock web environment, where there is nothing to configure anyway.
     */
    @Bean
    ServletContextInitializer webSocketContainerLimits() {
        return servletContext -> {
            Object container = servletContext.getAttribute(ServerContainer.class.getName());
            if (container instanceof ServerContainer serverContainer) {
                try {
                    serverContainer.setDefaultMaxSessionIdleTimeout(120_000L);
                    serverContainer.setDefaultMaxTextMessageBufferSize(16 * 1024);
                } catch (RuntimeException e) {
                    log.warn("Could not set the WebSocket limits: {}", e.getMessage());
                }
            }
        };
    }
}