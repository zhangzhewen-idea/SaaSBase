package com.saasbase.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FlywayMigrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("saasbase")
            .withUsername("root")
            .withPassword("rootpass");

    @Test
    void migrates_core_schema() throws Exception {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var result = connection.getMetaData().getTables(null, null, "iam_user", null)) {
            assertThat(result.next()).isTrue();
        }
    }

    @Test
    void migrates_role_permission_management_schema_and_permissions() throws Exception {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
            assertThat(columnExists(connection, "iam_role", "data_scope")).isTrue();
            assertThat(columnExists(connection, "iam_user", "session_version")).isTrue();

            try (var statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM iam_permission WHERE permission_code LIKE 'iam:%'");
                 var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(8);
            }
        }
    }

    private boolean columnExists(java.sql.Connection connection, String table, String column) throws SQLException {
        try (var result = connection.getMetaData().getColumns(null, null, table, column)) {
            return result.next();
        }
    }
}
