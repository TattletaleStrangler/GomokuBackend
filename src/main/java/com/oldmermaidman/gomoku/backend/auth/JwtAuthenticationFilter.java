package com.oldmermaidman.gomoku.backend.auth;

import com.oldmermaidman.gomoku.backend.common.ApiError;
import com.oldmermaidman.gomoku.backend.common.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads {@code Authorization: Bearer <token>} and puts the player into the security
 * context.
 *
 * <p>A missing token is not an error here — the request simply stays anonymous and
 * the security rules answer with 401. A token that is present but broken is recorded
 * on the request, so the entry point can report {@code TOKEN_EXPIRED} or
 * {@code TOKEN_INVALID} instead of a generic 401: the client needs the difference to
 * decide whether to refresh.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute carrying the reason a present token was rejected. */
    public static final String AUTH_ERROR_ATTRIBUTE = "app.auth.error";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                AuthenticatedUser user = jwtService.verify(header.substring(BEARER_PREFIX.length()).trim());
                var authentication = new UsernamePasswordAuthenticationToken(
                        user, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ApiException e) {
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.error());
            }
        }
        chain.doFilter(request, response);
    }
}