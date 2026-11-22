package com.oldmermaidman.gomoku.backend.auth;

import com.oldmermaidman.gomoku.backend.common.ApiError;
import com.oldmermaidman.gomoku.backend.common.ErrorWriter;
import com.oldmermaidman.gomoku.backend.common.RateLimiter;
import com.oldmermaidman.gomoku.backend.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Caps how often one address may call the login endpoints.
 *
 * <p>This runs before the security filter chain, so a flood is rejected before any
 * password hashing happens. The real client address is taken from
 * {@code X-Forwarded-For} via {@code server.forward-headers-strategy=framework}:
 * the application port itself is never published, only the reverse proxy reaches it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH = "/api/v1/auth/";

    private final RateLimiter limiter;
    private final RateLimitProperties properties;
    private final ErrorWriter errorWriter;

    public AuthRateLimitFilter(RateLimiter limiter, RateLimitProperties properties, ErrorWriter errorWriter) {
        this.limiter = limiter;
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(AUTH_PATH);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String key = "auth-ip:" + request.getRemoteAddr();
        if (!limiter.tryAcquire(key, properties.perIp(), properties.ipWindow())) {
            errorWriter.write(response, ApiError.RATE_LIMITED, "Too many requests, slow down");
            return;
        }
        chain.doFilter(request, response);
    }
}