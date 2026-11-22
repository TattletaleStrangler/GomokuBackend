package com.oldmermaidman.gomoku.backend.testsupport;

import com.jayway.jsonpath.JsonPath;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Base for API tests: the application on a random port against a real PostgreSQL,
 * plus a tiny HTTP client.
 *
 * <p>Requests go through the real filter chain and JSON mapping, so these tests see
 * exactly what the Android client will see. Subclasses add {@code @TestPropertySource}
 * when they need different limits; that also gives them their own context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
public abstract class ApiTestSupport {

    public static final String TEST_SECRET = "integration-test-secret-0123456789abcdef";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> TEST_SECRET);
    }

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    /** The random port of this test's server, for socket clients. */
    protected int port() {
        return port;
    }

    /** A response body read through JsonPath: status plus the fields that matter. */
    public record Response(int status, String body) {

        public String text(String path) {
            return JsonPath.read(body, path);
        }

        public int number(String path) {
            return ((Number) JsonPath.read(body, path)).intValue();
        }

        public boolean has(String path) {
            try {
                return JsonPath.read(body, path) != null;
            } catch (RuntimeException e) {
                return false;
            }
        }
    }

    /** Nicknames are unique, so every test uses its own. */
    protected static String nickname() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    protected static String credentials(String nickname, String password) {
        return "{\"nickname\":\"" + nickname + "\",\"password\":\"" + password + "\"}";
    }

    protected static String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }

    protected Response register(String nickname, String password) throws Exception {
        return post("/api/v1/auth/register", credentials(nickname, password), null);
    }

    protected Response post(String path, String json, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    protected Response post(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    protected Response get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    protected Response delete(String path, String json, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return send(builder.build());
    }

    private Response send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}