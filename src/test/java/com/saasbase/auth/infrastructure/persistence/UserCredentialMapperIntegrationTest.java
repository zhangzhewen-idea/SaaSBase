package com.saasbase.auth.infrastructure.persistence;

import com.saasbase.auth.domain.UserCredential;
import com.saasbase.auth.domain.gateway.UserCredentialGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class UserCredentialMapperIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserCredentialGateway gateway;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM iam_role_permission");
        jdbcTemplate.update("DELETE FROM iam_user_role");
        jdbcTemplate.update("DELETE FROM iam_permission");
        jdbcTemplate.update("DELETE FROM iam_role");
        jdbcTemplate.update("DELETE FROM iam_user");
        jdbcTemplate.update("DELETE FROM tenant");
    }

    @Test
    void findsActiveUserInActiveTenant() {
        insertTenant(1L, "tenant-a", "ACTIVE");
        insertUser(11L, 1L, "alice", "hash-a", "ACTIVE", false);

        assertThat(gateway.findByTenantCodeAndUsername("tenant-a", "alice"))
                .get()
                .extracting(UserCredential::tenantId, UserCredential::username, UserCredential::passwordHash)
                .containsExactly(1L, "alice", "hash-a");
    }

    @Test
    void doesNotFindDisabledUser() {
        insertTenant(1L, "tenant-a", "ACTIVE");
        insertUser(11L, 1L, "disabled", "hash-disabled", "DISABLED", false);

        assertThat(gateway.findByTenantCodeAndUsername("tenant-a", "disabled")).isEmpty();
    }

    @Test
    void doesNotFindLogicallyDeletedUser() {
        insertTenant(1L, "tenant-a", "ACTIVE");
        insertUser(11L, 1L, "deleted", "hash-deleted", "ACTIVE", true);

        assertThat(gateway.findByTenantCodeAndUsername("tenant-a", "deleted")).isEmpty();
    }

    @Test
    void doesNotFindUserInLogicallyDeletedTenant() {
        insertTenant(1L, "tenant-a", "ACTIVE");
        insertUser(11L, 1L, "alice", "hash-a", "ACTIVE", false);
        jdbcTemplate.update("UPDATE tenant SET deleted = 1 WHERE id = ?", 1L);

        assertThat(gateway.findByTenantCodeAndUsername("tenant-a", "alice")).isEmpty();
    }

    @Test
    void doesNotFindUserInMissingTenant() {
        insertTenant(1L, "tenant-a", "ACTIVE");
        insertUser(11L, 1L, "alice", "hash-a", "ACTIVE", false);

        assertThat(gateway.findByTenantCodeAndUsername("missing", "alice")).isEmpty();
    }

    @Test
    void returnsCredentialsFromRequestedTenantWhenUsernameIsShared() {
        insertTenant(1L, "tenant-a", "ACTIVE");
        insertTenant(2L, "tenant-b", "ACTIVE");
        insertUser(11L, 1L, "alice", "hash-a", "ACTIVE", false);
        insertUser(22L, 2L, "alice", "hash-b", "ACTIVE", false);

        assertThat(gateway.findByTenantCodeAndUsername("tenant-b", "alice"))
                .get()
                .extracting(UserCredential::tenantId, UserCredential::passwordHash)
                .containsExactly(2L, "hash-b");
    }

    @Test
    void returnsAllPermissionsGrantedThroughUserRoles() {
        insertTenant(1L, "tenant-a", "ACTIVE");
        insertUser(11L, 1L, "alice", "hash-a", "ACTIVE", false);
        insertRole(101L, 1L, "operator");
        insertPermission(1001L, "user:read");
        insertPermission(1002L, "user:write");
        jdbcTemplate.update("INSERT INTO iam_user_role (tenant_id, user_id, role_id) VALUES (?, ?, ?)", 1L, 11L, 101L);
        jdbcTemplate.update("INSERT INTO iam_role_permission (tenant_id, role_id, permission_id) VALUES (?, ?, ?), (?, ?, ?)",
                1L, 101L, 1001L, 1L, 101L, 1002L);

        assertThat(gateway.findByTenantCodeAndUsername("tenant-a", "alice"))
                .get()
                .extracting(UserCredential::permissions)
                .isEqualTo(Set.of("user:read", "user:write"));
    }

    @Test
    void doesNotReturnPermissionsFromLogicallyDeletedRole() {
        insertTenant(1L, "tenant-a", "ACTIVE");
        insertUser(11L, 1L, "alice", "hash-a", "ACTIVE", false);
        insertRole(101L, 1L, "deleted-role");
        insertPermission(1001L, "user:read");
        jdbcTemplate.update("UPDATE iam_role SET deleted = 1 WHERE id = ?", 101L);
        jdbcTemplate.update("INSERT INTO iam_user_role (tenant_id, user_id, role_id) VALUES (?, ?, ?)", 1L, 11L, 101L);
        jdbcTemplate.update("INSERT INTO iam_role_permission (tenant_id, role_id, permission_id) VALUES (?, ?, ?)",
                1L, 101L, 1001L);

        assertThat(gateway.findByTenantCodeAndUsername("tenant-a", "alice"))
                .get()
                .extracting(UserCredential::permissions)
                .isEqualTo(Set.of());
    }

    @Test
    void deduplicatesPermissionGrantedThroughMultipleRoles() {
        insertTenant(1L, "tenant-a", "ACTIVE");
        insertUser(11L, 1L, "alice", "hash-a", "ACTIVE", false);
        insertRole(101L, 1L, "operator");
        insertRole(102L, 1L, "auditor");
        insertPermission(1001L, "user:read");
        jdbcTemplate.update("INSERT INTO iam_user_role (tenant_id, user_id, role_id) VALUES (?, ?, ?), (?, ?, ?)",
                1L, 11L, 101L, 1L, 11L, 102L);
        jdbcTemplate.update("INSERT INTO iam_role_permission (tenant_id, role_id, permission_id) VALUES (?, ?, ?), (?, ?, ?)",
                1L, 101L, 1001L, 1L, 102L, 1001L);

        assertThat(gateway.findByTenantCodeAndUsername("tenant-a", "alice"))
                .get()
                .extracting(UserCredential::permissions)
                .isEqualTo(Set.of("user:read"));
    }

    private void insertTenant(long id, String code, String status) {
        jdbcTemplate.update("INSERT INTO tenant (id, tenant_code, tenant_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, code, code, status, now(), now());
    }

    private void insertUser(long id, long tenantId, String username, String hash, String status, boolean deleted) {
        jdbcTemplate.update("INSERT INTO iam_user (id, tenant_id, username, password_hash, display_name, status, created_at, updated_at, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, username, hash, username, status, now(), now(), deleted);
    }

    private void insertRole(long id, long tenantId, String code) {
        jdbcTemplate.update("INSERT INTO iam_role (id, tenant_id, role_code, role_name, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, tenantId, code, code, now(), now());
    }

    private void insertPermission(long id, String code) {
        jdbcTemplate.update("INSERT INTO iam_permission (id, permission_code, permission_name, permission_type, created_at) VALUES (?, ?, ?, ?, ?)",
                id, code, code, "API", now());
    }

    private Timestamp now() {
        return Timestamp.from(Instant.now());
    }
}
