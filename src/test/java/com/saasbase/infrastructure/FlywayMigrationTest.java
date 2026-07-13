package com.saasbase.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;

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
            assertThat(tableExists(connection, "iam_user")).isTrue();
            assertThat(tableExists(connection, "file_metadata")).isTrue();
            assertThat(columnExists(connection, "file_metadata", "extension")).isTrue();
            assertThat(columnExists(connection, "file_metadata", "status")).isTrue();
            assertThat(columnExists(connection, "file_metadata", "deleted_at")).isTrue();
            assertThat(columnExists(connection, "file_metadata", "version")).isTrue();
            assertThat(indexExists(connection, "file_metadata", "idx_file_metadata_tenant_type_time")).isTrue();
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        try (var result = connection.getMetaData().getTables(null, null, tableName, null)) {
            return result.next();
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws Exception {
        try (var result = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            return result.next();
        }
    }

    private boolean indexExists(Connection connection, String tableName, String indexName) throws Exception {
        try (var result = connection.getMetaData().getIndexInfo(null, null, tableName, false, false)) {
            while (result.next()) {
                if (indexName.equals(result.getString("INDEX_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
