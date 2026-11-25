package com.oldmermaidman.gomoku.backend.config;

import com.oldmermaidman.gomoku.backend.auth.JwtAuthenticationFilter;
import com.oldmermaidman.gomoku.backend.common.ApiError;
import com.oldmermaidman.gomoku.backend.common.ErrorWriter;
import com.oldmermaidman.gomoku.backend.common.RateLimiter;
import com.oldmermaidman.gomoku.backend.common.RequestLimitFilter;
import com.oldmermaidman.gomoku.backend.user.AdminUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Two security worlds (FEATURES.md, B4).
 *
 * <p>{@code /admin/**} is a server-rendered panel for the owner: form login with a
 * session, CSRF on, and only the account with the {@code ADMIN} role gets in.
 *
 * <p>Everything else is the stateless API: only registration, login and refresh are
 * open; a valid access token is needed everywhere else. Failures use the same error body
 * as the rest of the API, and a token that is present but broken keeps its own code
 * ({@code TOKEN_EXPIRED} / {@code TOKEN_INVALID}) so the client knows whether refreshing
 * can help.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Session-based admin panel; matched first so it never meets the JWT chain. */
    @Bean
    @Order(1)
    SecurityFilterChain adminFilterChain(
            HttpSecurity http,
            AdminUserDetailsService admins) throws Exception {

        http
                .securityMatcher("/admin/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .defaultSuccessUrl("/admin/reports", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login?logout"))
                .userDetailsService(admins);

        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            ErrorWriter errorWriter,
            RateLimiter rateLimiter) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                // A crash often happens before signing in; the report is
                                // worth more than the account it belongs to.
                                "/api/v1/crash-reports").permitAll()
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint((request, response, error) -> {
                    // A present but broken token keeps its own code, so the client knows
                    // whether refreshing it can help.
                    ApiError recorded = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE)
                            instanceof ApiError apiError ? apiError : ApiError.UNAUTHORIZED;
                    errorWriter.write(response, recorded, recorded.name());
                }))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // After the token has been read, so the limit follows the account; the
                // anonymous crash-report endpoint is counted by address instead.
                .addFilterAfter(
                        new RequestLimitFilter(rateLimiter, errorWriter),
                        JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // Strength 10 is the Spring default: strong enough, fast enough for a login.
        return new BCryptPasswordEncoder(10);
    }
}