package com.saasbase.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

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
            assertThat(tableExists(connection, "file_object")).isFalse();
            assertThat(columnExists(connection, "file_metadata", "original_filename")).isTrue();
            assertThat(columnExists(connection, "file_metadata", "filename")).isFalse();
            assertThat(columnMetadata(connection, "file_metadata", "extension"))
                    .isEqualTo(new ColumnMetadata(Types.VARCHAR, DatabaseMetaData.columnNoNulls, ""));
            assertThat(columnMetadata(connection, "file_metadata", "status"))
                    .isEqualTo(new ColumnMetadata(Types.VARCHAR, DatabaseMetaData.columnNoNulls, "AVAILABLE"));
            assertThat(columnExists(connection, "file_metadata", "deleted_at")).isTrue();
            assertThat(columnExists(connection, "file_metadata", "version")).isTrue();
            assertThat(indexColumns(connection, "file_metadata", "idx_file_metadata_tenant_type_time"))
                    .containsExactly("tenant_id", "deleted", "content_type", "created_at");
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

    private ColumnMetadata columnMetadata(Connection connection, String tableName, String columnName) throws Exception {
        try (var result = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            assertThat(result.next()).isTrue();
            return new ColumnMetadata(
                    result.getInt("DATA_TYPE"),
                    result.getInt("NULLABLE"),
                    result.getString("COLUMN_DEF"));
        }
    }

    private List<String> indexColumns(Connection connection, String tableName, String indexName) throws Exception {
        var columns = new ArrayList<String>();
        try (var result = connection.getMetaData().getIndexInfo(null, null, tableName, false, false)) {
            while (result.next()) {
                if (indexName.equals(result.getString("INDEX_NAME"))) {
                    columns.add(result.getString("COLUMN_NAME"));
                }
            }
        }
        return columns;
    }

    private record ColumnMetadata(int dataType, int nullable, String defaultValue) {
    }
}
