package com.oldmermaidman.gomoku.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the whole application against a real PostgreSQL.
 *
 * <p>The tests check that the migrations actually ran and produced the schema, not
 * just that the context came up: a missing Flyway auto-configuration once left the
 * application running happily against an empty database. Without a running Docker
 * engine the class is skipped instead of failing, so unit tests still work elsewhere.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class GomokuBackendApplicationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String TEST_SECRET = "integration-test-secret-0123456789abcdef";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> TEST_SECRET);
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void migrationsCreateTheSchema() throws Exception {
        Set<String> tables = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select table_name from information_schema.tables where table_schema = 'public'")) {
            while (rows.next()) {
                tables.add(rows.getString(1));
            }
        }

        assertThat(tables).contains(
                "users", "refresh_tokens", "games", "moves", "flyway_schema_history");
    }

    @Test
    void theFirstMigrationIsRecordedAsSuccessful() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select version, success from flyway_schema_history order by installed_rank")) {

            assertThat(rows.next()).as("V1 must be recorded").isTrue();
            assertThat(rows.getString("version")).isEqualTo("1");
            assertThat(rows.getBoolean("success")).isTrue();
        }
    }
}