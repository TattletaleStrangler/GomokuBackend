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
import java.util.Locale;

/**
 * Caps how often one address may call the login endpoints, and how often one nickname
 * may fail to sign in to the admin panel (security audit, P1).
 *
 * <p>This runs before the security filter chain, so a flood is rejected before any
 * password hashing happens. The real client address is taken from
 * {@code X-Forwarded-For} via {@code server.forward-headers-strategy=framework}:
 * the application port itself is never published, only the reverse proxy reaches it.
 *
 * <p>The admin form login is a separate Spring Security chain with no lockout of its
 * own, so the per-nickname counter lives here: five failures in fifteen minutes and the
 * form answers "too many attempts" until the window passes. A successful sign-in clears
 * the counter.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH = "/api/v1/auth/";
    private static final String ADMIN_LOGIN_PATH = "/admin/login";

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
        String uri = request.getRequestURI();
        if (uri.startsWith(AUTH_PATH)) {
            return false;
        }
        // Only the sign-in POST of the admin panel, not its pages.
        return !ADMIN_LOGIN_PATH.equals(uri) || !"POST".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String address = request.getRemoteAddr();
        if (!limiter.tryAcquire("auth-ip:" + address, properties.perIp(), properties.ipWindow())) {
            errorWriter.write(response, ApiError.RATE_LIMITED, "Too many requests, slow down");
            return;
        }

        if (!ADMIN_LOGIN_PATH.equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String nickname = request.getParameter("username");
        String nicknameKey = nickname == null || nickname.isBlank() ? null : loginKey(nickname);
        if (nicknameKey != null
                && limiter.isExceeded(
                        nicknameKey, properties.maxFailedLogins(), properties.failedLoginWindow())) {
            response.sendRedirect(ADMIN_LOGIN_PATH + "?locked");
            return;
        }

        chain.doFilter(request, response);

        if (nicknameKey == null) {
            return;
        }
        if (signedIn(response)) {
            limiter.reset(nicknameKey);
        } else if (refused(response)) {
            // Counts the attempt without letting it through the limit.
            limiter.tryAcquire(
                    nicknameKey, properties.maxFailedLogins(), properties.failedLoginWindow());
        }
    }

    /** The bucket one administrator's failed attempts are counted in. Visible for tests. */
    public static String loginKey(String nickname) {
        return "login:" + nickname.trim().toLowerCase(Locale.ROOT);
    }

    /** The form answers a failure with a redirect back to the page carrying {@code error}. */
    private static boolean refused(HttpServletResponse response) {
        return response.getStatus() == 302
                && location(response) != null
                && location(response).contains("error");
    }

    private static boolean signedIn(HttpServletResponse response) {
        String location = location(response);
        return response.getStatus() == 302
                && location != null
                && !location.contains("error")
                && !location.contains("locked");
    }

    private static String location(HttpServletResponse response) {
        return response.getHeader("Location");
    }
}