package com.saasbase.iam.infrastructure.persistence;

import com.saasbase.common.tenant.TenantContextHolder;
import com.saasbase.common.tenant.TenantContext;
import com.saasbase.iam.domain.IamUser;
import com.saasbase.iam.domain.UserPageQuery;
import com.saasbase.iam.domain.UserStatus;
import org.junit.jupiter.api.AfterEach;
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
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class UserPersistenceAdapterIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserPersistenceAdapter adapter;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void cleanDatabase() {
        TenantContextHolder.clear();
        jdbcTemplate.update("DELETE FROM iam_user_role");
        jdbcTemplate.update("DELETE FROM iam_role_permission");
        jdbcTemplate.update("DELETE FROM iam_permission");
        jdbcTemplate.update("DELETE FROM iam_user");
        jdbcTemplate.update("DELETE FROM iam_role");
        jdbcTemplate.update("DELETE FROM iam_dept");
        jdbcTemplate.update("DELETE FROM tenant");
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void detectsUsernameConflictWithinTenantOnly() {
        insertTenant(1L, "tenant-a");
        insertTenant(2L, "tenant-b");
        insertUser(11L, 1L, "alice", "ACTIVE", 0L, 1L);
        insertUser(22L, 2L, "alice", "ACTIVE", 0L, 1L);

        assertThat(adapter.existsByUsername(1L, "alice")).isTrue();
        assertThat(adapter.existsByUsername(2L, "alice")).isTrue();
        assertThat(adapter.existsByUsername(1L, "bob")).isFalse();
    }

    @Test
    void rejectsCrossTenantReferencesAndAcceptsLocalOnes() {
        insertTenant(1L, "tenant-a");
        insertTenant(2L, "tenant-b");
        insertDept(101L, 1L, "ACTIVE");
        insertDept(202L, 2L, "ACTIVE");
        insertRole(301L, 1L, "ACTIVE");
        insertRole(402L, 2L, "ACTIVE");

        assertThatThrownBy(() -> adapter.assertDepartmentActive(1L, 202L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.assertRoleActive(1L, 402L))
                .isInstanceOf(IllegalArgumentException.class);
        adapter.assertDepartmentActive(1L, 101L);
        adapter.assertRoleActive(1L, 301L);
    }

    @Test
    void pagesInStableOrderAndHonorsOptimisticLocking() {
        insertTenant(1L, "tenant-a");
        insertUser(11L, 1L, "alice", "ACTIVE", 3L, 1L);
        insertUser(12L, 1L, "bob", "ACTIVE", 5L, 5L);
        insertUser(13L, 1L, "carol", "ACTIVE", 4L, 4L);
        TenantContextHolder.set(new TenantContext(1L, 12L, false));

        assertThat(adapter.page(1L, 12L, new UserPageQuery(1, 10, null, null, null, null)).items())
                .extracting(IamUser::id)
                .containsExactly(13L, 11L);

        assertThat(adapter.page(1L, 99L, new UserPageQuery(1, 10, null, null, null, null)).items())
                .extracting(IamUser::id)
                .containsExactly(13L, 12L, 11L);

        IamUser bob = adapter.findById(1L, 12L).orElseThrow();
        bob.resetPassword("new-hash");
        assertThat(adapter.update(bob)).isTrue();
        assertThat(adapter.update(bob)).isFalse();
    }

    @Test
    void pagesByUsernameSubstring() {
        insertTenant(1L, "tenant-a");
        insertUser(11L, 1L, "alice", "ACTIVE", 0L, 1L);
        insertUser(12L, 1L, "alicia", "ACTIVE", 0L, 1L);
        insertUser(13L, 1L, "bob", "ACTIVE", 0L, 1L);
        TenantContextHolder.set(new TenantContext(1L, 99L, false));

        assertThat(adapter.page(1L, 99L, new UserPageQuery(1, 10, "ali", null, null, null)).items())
                .extracting(IamUser::username)
                .containsExactlyInAnyOrder("alice", "alicia");
    }

    @Test
    void replacesRolesCompletelyWithinTenant() {
        insertTenant(1L, "tenant-a");
        insertRole(101L, 1L, "ACTIVE");
        insertRole(102L, 1L, "ACTIVE");
        insertRole(103L, 1L, "ACTIVE");
        insertUser(11L, 1L, "alice", "ACTIVE", 0L, 1L);
        jdbcTemplate.update("INSERT INTO iam_user_role (tenant_id, user_id, role_id) VALUES (?, ?, ?), (?, ?, ?)",
                1L, 11L, 101L, 1L, 11L, 102L);

        adapter.replaceRoles(1L, 11L, new LinkedHashSet<>(Set.of(102L, 103L)));

        assertThat(adapter.findRoleIds(1L, 11L)).containsExactlyInAnyOrder(102L, 103L);
    }

    @Test
    void countsAdministratorsExcludingTargetUser() {
        insertTenant(1L, "tenant-a");
        insertRole(9001L, 1L, "ACTIVE", "TENANT_ADMIN");
        insertRole(9002L, 1L, "ACTIVE", "OPERATOR");
        insertUser(11L, 1L, "alice", "ACTIVE", 0L, 1L);
        insertUser(12L, 1L, "bob", "ACTIVE", 0L, 1L);
        insertUser(13L, 1L, "carol", "DISABLED", 0L, 1L);
        jdbcTemplate.update("INSERT INTO iam_user_role (tenant_id, user_id, role_id) VALUES (?, ?, ?), (?, ?, ?), (?, ?, ?)",
                1L, 11L, 9001L, 1L, 12L, 9001L, 1L, 13L, 9001L);

        assertThat(adapter.countActiveAdministratorsExcludingUser(1L, 11L)).isEqualTo(1L);
    }

    private void insertTenant(long id, String code) {
        jdbcTemplate.update("INSERT INTO tenant (id, tenant_code, tenant_name, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                id, code, code, now(), now());
    }

    private void insertUser(long id, long tenantId, String username, String status, long sessionVersion, long version) {
        insertUserAt(id, tenantId, username, status, sessionVersion, version, Timestamp.valueOf("2026-07-13 08:00:00.000000"));
    }

    private void insertUserAt(long id, long tenantId, String username, String status, long sessionVersion, long version, Timestamp createdAt) {
        jdbcTemplate.update("""
                INSERT INTO iam_user
                (id, tenant_id, username, password_hash, display_name, phone, primary_department_id, status,
                 must_change_password, session_version, last_login_at, version, created_at, updated_at, deleted)
                VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, 0, ?, NULL, ?, ?, ?, 0)
                """,
                id, tenantId, username, "hash-" + username, username, status, sessionVersion, version, createdAt, createdAt);
    }

    private void insertDept(long id, long tenantId, String status) {
        jdbcTemplate.update("INSERT INTO iam_dept (id, tenant_id, parent_id, dept_code, dept_name, status, created_at, updated_at, deleted) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, 0)",
                id, tenantId, "dept-" + id, "dept-" + id, status, now(), now());
    }

    private void insertRole(long id, long tenantId, String status) {
        insertRole(id, tenantId, status, "ROLE-" + id);
    }

    private void insertRole(long id, long tenantId, String status, String code) {
        jdbcTemplate.update("INSERT INTO iam_role (id, tenant_id, role_code, role_name, status, created_at, updated_at, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
                id, tenantId, code, code, status, now(), now());
    }

    private Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    private Timestamp timestamp(String text) {
        return Timestamp.valueOf(text.replace('T', ' '));
    }
}
