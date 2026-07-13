package com.saasbase.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.LinkedHashMap;
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
    void migrates_core_schema_and_tenant_management_baseline() throws Exception {
        cleanDatabase();
        flyway().migrate();

        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
            var expectedCoreTables = Set.of("tenant", "iam_user", "iam_role", "iam_permission");
            try (var tables = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
                var actualTables = new java.util.HashSet<String>();
                while (tables.next()) {
                    actualTables.add(tables.getString("TABLE_NAME"));
                }
                assertThat(actualTables).containsAll(expectedCoreTables);
            }

            try (var statement = connection.prepareStatement("""
                    SELECT IS_NULLABLE, COLUMN_DEFAULT
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = 'tenant'
                      AND column_name = 'session_version'
                    """);
                 var column = statement.executeQuery()) {
                assertThat(column.next()).isTrue();
                assertThat(column.getString("IS_NULLABLE")).isEqualTo("NO");
                assertThat(String.valueOf(column.getObject("COLUMN_DEFAULT"))).isEqualTo("0");
                assertThat(column.next()).isFalse();
            }

            try (var statement = connection.prepareStatement("""
                    SELECT EXTRA FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'tenant' AND column_name = 'id'
                    """); var column = statement.executeQuery()) {
                assertThat(column.next()).isTrue();
                assertThat(column.getString("EXTRA")).contains("auto_increment");
            }

            var expectedPermissions = new LinkedHashMap<String, Permission>();
            expectedPermissions.put("platform:tenant:create", new Permission(900000000000000101L, "创建租户"));
            expectedPermissions.put("platform:tenant:read", new Permission(900000000000000102L, "查看租户"));
            expectedPermissions.put("platform:tenant:update", new Permission(900000000000000103L, "更新租户"));
            expectedPermissions.put("platform:tenant:enable", new Permission(900000000000000104L, "启用租户"));
            expectedPermissions.put("platform:tenant:disable", new Permission(900000000000000105L, "停用租户"));
            expectedPermissions.put("tenant:profile:read", new Permission(900000000000000106L, "查看租户资料"));

            try (var statement = connection.createStatement();
                 var permissions = statement.executeQuery("""
                         SELECT id, permission_code, permission_name, permission_type, created_at
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
                var remainingPermissions = new LinkedHashMap<>(expectedPermissions);
                while (permissions.next()) {
                    var permission = remainingPermissions.remove(permissions.getString("permission_code"));
                    assertThat(permission).isNotNull();
                    assertThat(permissions.getLong("id")).isEqualTo(permission.id());
                    assertThat(permissions.getString("permission_name")).isEqualTo(permission.name());
                    assertThat(permissions.getString("permission_type")).isEqualTo("API");
                    assertThat(permissions.getTimestamp("created_at")).isNotNull();
                }
                assertThat(remainingPermissions).isEmpty();
            }
        }
    }

    @Test
    void migrates_existing_tenant_from_v2_to_v3_with_default_session_version() throws Exception {
        cleanDatabase();
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .target("2")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO tenant (
                        id, tenant_code, tenant_name, status, created_at, updated_at
                    ) VALUES (
                        1, 'existing-tenant', '存量租户', 'ENABLED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                    )
                    """);
            statement.executeUpdate("INSERT INTO iam_user (id, tenant_id, username, password_hash, display_name, status, created_at, updated_at) VALUES (41, 1, 'old', 'hash', 'Old', 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))");
            statement.executeUpdate("INSERT INTO iam_role (id, tenant_id, role_code, role_name, created_at, updated_at) VALUES (51, 1, 'OLD', 'Old', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))");
        }

        flyway().migrate();

        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement();
             var tenant = statement.executeQuery("SELECT id, session_version FROM tenant WHERE id = 1")) {
            assertThat(tenant.next()).isTrue();
            assertThat(tenant.getLong("id")).isEqualTo(1L);
            assertThat(tenant.getLong("session_version")).isZero();
            assertThat(tenant.next()).isFalse();
            statement.executeUpdate("INSERT INTO iam_user (tenant_id, username, password_hash, display_name, status, created_at, updated_at) VALUES (1, 'new', 'hash', 'New', 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))");
            statement.executeUpdate("INSERT INTO iam_role (tenant_id, role_code, role_name, created_at, updated_at) VALUES (1, 'NEW', 'New', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))");
            try (var existing = statement.executeQuery("SELECT id FROM iam_user WHERE username='old'")) {
                assertThat(existing.next()).isTrue(); assertThat(existing.getLong(1)).isEqualTo(41L);
            }
            try (var generated = statement.executeQuery("SELECT id FROM iam_user WHERE username='new'")) {
                assertThat(generated.next()).isTrue(); assertThat(generated.getLong(1)).isGreaterThan(41L);
            }
            try (var existing = statement.executeQuery("SELECT id FROM iam_role WHERE role_code='OLD'")) {
                assertThat(existing.next()).isTrue(); assertThat(existing.getLong(1)).isEqualTo(51L);
            }
            try (var generated = statement.executeQuery("SELECT id FROM iam_role WHERE role_code='NEW'")) {
                assertThat(generated.next()).isTrue(); assertThat(generated.getLong(1)).isGreaterThan(51L);
            }
            statement.executeUpdate("""
                    INSERT INTO tenant (tenant_code, tenant_name, status, created_at, updated_at)
                    VALUES ('new-tenant', '新租户', 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """);
            try (var generated = statement.executeQuery("SELECT id FROM tenant WHERE tenant_code = 'new-tenant'")) {
                assertThat(generated.next()).isTrue();
                assertThat(generated.getLong("id")).isGreaterThan(1L).isNotNegative();
            }
        }
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load();
    }

    private void cleanDatabase() {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .cleanDisabled(false)
                .load()
                .clean();
    }

    private record Permission(long id, String name) {
    }
}
