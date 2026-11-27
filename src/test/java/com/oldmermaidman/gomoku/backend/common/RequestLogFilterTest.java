package com.oldmermaidman.gomoku.backend.common;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.oldmermaidman.gomoku.backend.auth.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The line that makes a misbehaving screen answerable: it has to name the call, its
 * status, the account and the request id, it has to cover a refused call, and it must
 * stay out of the way of static requests and of tests.
 */
class RequestLogFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        SecurityContextHolder.clearContext();
    }

    private void signedIn(String nickname) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(UUID.randomUUID(), nickname), null, List.of()));
    }

    private FilterChain chainReturning(int status) {
        return (request, response) -> ((MockHttpServletResponse) response).setStatus(status);
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void logsTheCallWithItsStatusAndAccount() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/games/lobby");
        request.setQueryString("rule=RENJU&timeControl=BLITZ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        signedIn("ann");

        new RequestLogFilter(true).doFilter(request, response, chainReturning(200));

        assertEquals(1, messages().size());
        String line = messages().get(0);
        assertTrue(line.contains("GET /api/v1/games/lobby?rule=RENJU&timeControl=BLITZ"), line);
        assertTrue(line.contains("-> 200"), line);
        assertTrue(line.contains("user=ann"), line);
    }

    @Test
    void logsARefusedCallToo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/games/quick");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("X-Request-Id", "req-42");

        new RequestLogFilter(true).doFilter(request, response, chainReturning(429));

        String line = messages().get(0);
        assertTrue(line.contains("-> 429"), line);
        // Anonymous: the token was missing or broken, which is exactly what a log has to show.
        assertTrue(line.contains("user=-"), line);
        assertTrue(line.contains("requestId=req-42"), line);
    }

    @Test
    void staysOutOfStaticRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/favicon.ico");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestLogFilter(true).doFilter(request, response, chainReturning(200));

        assertTrue(messages().isEmpty(), "static requests are not API calls");
    }

    @Test
    void saysNothingWhenSwitchedOff() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/games/lobby");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestLogFilter(false).doFilter(request, response, chainReturning(200));

        assertFalse(appender.list.stream().findAny().isPresent(), "app.log.requests=false is quiet");
    }
}
