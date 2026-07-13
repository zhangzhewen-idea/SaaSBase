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
            try (var columns = connection.getMetaData().getColumns(null, null, "iam_user", null)) {
                var columnNames = new java.util.HashSet<String>();
                while (columns.next()) {
                    columnNames.add(columns.getString("COLUMN_NAME"));
                }
                assertThat(columnNames).contains(
                        "phone",
                        "primary_department_id",
                        "must_change_password",
                        "session_version",
                        "last_login_at"
                );
            }

            try (var statement = connection.createStatement();
                 var permissions = statement.executeQuery("SELECT permission_code FROM iam_permission")) {
                var permissionCodes = new java.util.HashSet<String>();
                while (permissions.next()) {
                    permissionCodes.add(permissions.getString("permission_code"));
                }
                assertThat(permissionCodes).containsAll(Set.of(
                        "tenant:user:create",
                        "tenant:user:read",
                        "tenant:user:update",
                        "tenant:user:enable",
                        "tenant:user:disable",
                        "tenant:user:reset-password"
                ));
            }
        }
    }
}
