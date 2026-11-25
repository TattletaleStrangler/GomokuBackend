package com.oldmermaidman.gomoku.backend.common;

import com.oldmermaidman.gomoku.backend.auth.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

/**
 * Per-player limits on the calls that change state (security audit, P1).
 *
 * <p>The authentication endpoints have their own limiter; this one covers everything a
 * signed-in player can spam — moves, joining by code, quick match, undo and draw
 * offers, blocking, reporting — and the anonymous crash-report endpoint, which is
 * counted per address instead. Reads are not limited: they are cheap, bounded and
 * needed by every screen.
 *
 * <p>The filter runs inside the API security chain, after the token has been read, so
 * the user is known and the limit follows the account rather than the address. A
 * player behind one NAT is therefore not punished for somebody else's flood.
 */
public class RequestLimitFilter extends OncePerRequestFilter {

    /** No hand can make more moves than this in a minute. */
    static final int MOVE_LIMIT = 30;

    /** Roomy for a person, useless for a script opening and closing games. */
    static final int GAME_LIMIT = 10;

    /** Crash reports may come from a device that never signed in, so they are per address. */
    static final int CRASH_LIMIT = 20;

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration HOUR = Duration.ofHours(1);

    private static final String CRASH_PATH = "/api/v1/crash-reports";

    /** The game calls a player may make by hand, and nothing else, are limited. */
    private static final Set<String> GAME_ACTIONS = Set.of(
            "resign", "leave", "undo-request", "undo-decision",
            "draw-offer", "draw-decision", "block", "report");

    private final RateLimiter limiter;
    private final ErrorWriter errorWriter;

    public RequestLimitFilter(RateLimiter limiter, ErrorWriter errorWriter) {
        this.limiter = limiter;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        int limit;
        Duration window;
        boolean byAddress;

        if (CRASH_PATH.equals(path)) {
            limit = CRASH_LIMIT;
            window = HOUR;
            byAddress = true;
        } else if (path.endsWith("/moves")) {
            limit = MOVE_LIMIT;
            window = MINUTE;
            byAddress = false;
        } else if (isGuardedGameCall(path)) {
            limit = GAME_LIMIT;
            window = MINUTE;
            byAddress = false;
        } else {
            chain.doFilter(request, response);
            return;
        }

        String subject = byAddress ? request.getRemoteAddr() : subjectOf(request);
        if (!limiter.tryAcquire(key(path, subject), limit, window)) {
            errorWriter.write(response, ApiError.RATE_LIMITED, "Too many requests, slow down");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * The key a call uses: the endpoint with the game id replaced by a placeholder, so
     * one player's moves do not count against their joins, and the subject (account or
     * address). Visible for tests that need to reset a bucket.
     */
    public static String key(String path, String subject) {
        String bucket = path.replaceAll("/[0-9a-fA-F-]{36}", "/{id}");
        return "limit:" + bucket + ":" + subject;
    }

    private static String subjectOf(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id().toString();
        }
        return request.getRemoteAddr();
    }

    /**
     * The state-changing game calls: creating and leaving a room, joining by code,
     * quick match, and everything offered from the game screen.
     */
    private static boolean isGuardedGameCall(String path) {
        if (!path.startsWith("/api/v1/games")) {
            return false;
        }
        if (path.equals("/api/v1/games")
                || path.equals("/api/v1/games/join")
                || path.equals("/api/v1/games/quick")) {
            return true;
        }
        int slash = path.lastIndexOf('/');
        return slash > 0 && GAME_ACTIONS.contains(path.substring(slash + 1));
    }
}