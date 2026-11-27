package com.oldmermaidman.gomoku.backend.common;

import com.oldmermaidman.gomoku.backend.auth.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * One line per API call: method, path, status, duration, account and request id.
 *
 * <p>Written because a screen that misbehaves leaves no trace anywhere else. The game
 * endpoints answer a perfectly valid "nobody is waiting, here is your own room", and a
 * refused call is a 4xx domain error — while the admin panel collects 5xx only. So
 * "the app does nothing" used to be unanswerable: this line says whether the phone sent
 * anything at all and what came back.
 *
 * <p>The path is logged together with its query, which is where the filters of a lobby
 * call live. Request bodies and headers are never logged: passwords travel in one and
 * tokens in the other.
 *
 * <p>The account comes from the token, so a call refused before authentication shows
 * {@code user=-}. Switching the line off is `app.log.requests=false` (tests do that).
 */
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    private final boolean enabled;

    public RequestLogFilter(boolean enabled) {
        this.enabled = enabled;
    }

    /** The health probe of the container is not an API call worth a line. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !enabled || !(path.startsWith("/api/") || path.startsWith("/admin"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - started) / 1_000_000;
            String query = request.getQueryString();
            String requestId = response.getHeader("X-Request-Id");
            log.info("{} {}{} -> {} ({} ms) user={} requestId={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    query == null ? "" : "?" + query,
                    response.getStatus(),
                    millis,
                    account(),
                    requestId == null ? "-" : requestId);
        }
    }

    /** The nickname of the signed-in player, or a dash while the call is anonymous. */
    private String account() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthenticatedUser user
                ? user.nickname()
                : "-";
    }
}
