package com.oldmermaidman.gomoku.backend.common;

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
 * Rejects request bodies that are far larger than anything the API accepts
 * (security audit, P1).
 *
 * <p>Every body the client sends is a small JSON document; the largest field is a crash
 * stack trace, capped at 20 000 characters by validation. Without this check a client
 * could stream megabytes into any endpoint and make the server buffer them. A request
 * whose body arrives chunked is refused as well: a body that cannot be measured cannot
 * be bounded. A request with no body at all is fine — it declares no length either, and
 * refusing it turned every body-less POST into a 413 instead of the answer it deserved.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeFilter extends OncePerRequestFilter {

    /** Comfortably above the 20 KB stack trace, far below anything that hurts. */
    static final long MAX_BODY_BYTES = 64 * 1024;

    private final ErrorWriter errorWriter;

    public RequestSizeFilter(ErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"POST".equals(method) && !"PUT".equals(method) && !"PATCH".equals(method)) {
            return true;
        }
        String path = request.getRequestURI();
        return !path.startsWith("/api/") && !path.startsWith("/admin/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        long declared = request.getContentLengthLong();
        boolean chunked = "chunked".equalsIgnoreCase(request.getHeader("Transfer-Encoding"));
        if (declared > MAX_BODY_BYTES || (declared < 0 && chunked)) {
            errorWriter.write(
                    response,
                    ApiError.PAYLOAD_TOO_LARGE,
                    "Request body is too large or arrives in chunks");
            return;
        }
        chain.doFilter(request, response);
    }
}