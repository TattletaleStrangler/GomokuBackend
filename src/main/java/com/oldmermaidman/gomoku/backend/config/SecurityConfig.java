package com.oldmermaidman.gomoku.backend.config;

import com.oldmermaidman.gomoku.backend.auth.JwtAuthenticationFilter;
import com.oldmermaidman.gomoku.backend.common.ApiError;
import com.oldmermaidman.gomoku.backend.common.ErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * Stateless JWT security.
 *
 * <p>Only registration, login and refresh are open; everything else needs a valid
 * access token. Failures use the same error body as the rest of the API, and a token
 * that is present but broken keeps its own code ({@code TOKEN_EXPIRED} /
 * {@code TOKEN_INVALID}) so the client knows whether refreshing can help.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            ErrorWriter errorWriter) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh").permitAll()
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
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // Strength 10 is the Spring default: strong enough, fast enough for a login.
        return new BCryptPasswordEncoder(10);
    }
}