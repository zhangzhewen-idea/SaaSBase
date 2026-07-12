package com.saasbase.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.Set;

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

        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
            try (var columns = connection.getMetaData().getColumns(null, null, "tenant", "session_version")) {
                assertThat(columns.next()).isTrue();
            }

            var expectedPermissionCodes = Set.of(
                    "platform:tenant:create",
                    "platform:tenant:read",
                    "platform:tenant:update",
                    "platform:tenant:enable",
                    "platform:tenant:disable",
                    "tenant:profile:read"
            );
            try (var statement = connection.createStatement();
                 var permissions = statement.executeQuery("""
                         SELECT permission_code
                         FROM iam_permission
                         WHERE permission_code IN (
                             'platform:tenant:create',
                             'platform:tenant:read',
                             'platform:tenant:update',
                             'platform:tenant:enable',
                             'platform:tenant:disable',
                             'tenant:profile:read'
                         )
                         """)) {
                var actualPermissionCodes = new java.util.HashSet<String>();
                while (permissions.next()) {
                    actualPermissionCodes.add(permissions.getString("permission_code"));
                }
                assertThat(actualPermissionCodes).containsExactlyInAnyOrderElementsOf(expectedPermissionCodes);
            }
        }
    }
}
