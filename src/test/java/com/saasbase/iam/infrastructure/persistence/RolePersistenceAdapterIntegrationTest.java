package com.saasbase.iam.infrastructure.persistence;

import com.saasbase.common.api.PageResponse;
import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.iam.domain.DataScope;
import com.saasbase.iam.domain.Role;
import com.saasbase.iam.domain.RoleStatus;
import com.saasbase.iam.domain.RoleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RolePersistenceAdapterIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("test")
            .withUsername("root")
            .withPassword("rootpass");

    @Autowired
    private RolePersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> mysql.getJdbcUrl()
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM iam_role_permission");
        jdbcTemplate.update("DELETE FROM iam_user_role");
        jdbcTemplate.update("DELETE FROM iam_role");
        jdbcTemplate.update("DELETE FROM tenant");
    }

    @Test
    void stores_and_pages_roles_by_tenant_only() {
        insertTenant(7L, "tenant-a");
        insertTenant(8L, "tenant-b");
        adapter.insert(Role.create(701L, 7L, "AUDITOR", "审计员", DataScope.SELF));
        adapter.insert(Role.create(801L, 8L, "AUDITOR", "审计员", DataScope.SELF));

        PageResponse<Role> page = adapter.page(7L, null, null, null, 1L, 10L);

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items()).extracting(Role::tenantId).containsExactly(7L);
    }

    @Test
    void soft_delete_hides_role_and_version_mismatch_returns_false() {
        insertTenant(7L, "tenant-a");
        adapter.insert(Role.create(702L, 7L, "AUDITOR", "审计员", DataScope.SELF));

        assertThat(adapter.update(Role.restore(702L, 7L, "AUDITOR", "审计负责人", RoleType.CUSTOM, RoleStatus.ACTIVE, DataScope.ALL, 1L), 1L))
                .isFalse();

        adapter.deleteRelationsAndSoftDelete(7L, 702L, 99L);
        assertThat(adapter.findById(7L, 702L)).isEmpty();
    }

    @Test
    void update_persists_role_changes_and_bumps_version() {
        insertTenant(7L, "tenant-a");
        adapter.insert(Role.create(704L, 7L, "AUDITOR", "审计员", DataScope.SELF));

        boolean updated = adapter.update(Role.restore(704L, 7L, "AUDITOR_LEAD", "审计负责人", RoleType.CUSTOM, RoleStatus.DISABLED, DataScope.ALL, 0L), 0L);

        assertThat(updated).isTrue();
        Role stored = adapter.findById(7L, 704L).orElseThrow();
        assertThat(stored.roleCode()).isEqualTo("AUDITOR_LEAD");
        assertThat(stored.roleName()).isEqualTo("审计负责人");
        assertThat(stored.status()).isEqualTo(RoleStatus.DISABLED);
        assertThat(stored.dataScope()).isEqualTo(DataScope.ALL);
        assertThat(stored.version()).isEqualTo(1L);
    }

    @Test
    void soft_delete_missing_row_throws_optimistic_lock_conflict() {
        insertTenant(7L, "tenant-a");
        adapter.insert(Role.create(703L, 7L, "AUDITOR", "审计员", DataScope.SELF));
        jdbcTemplate.update("DELETE FROM iam_role WHERE tenant_id = ? AND id = ?", 7L, 703L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.deleteRelationsAndSoftDelete(7L, 703L, 99L))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.IAM_OPTIMISTIC_LOCK_CONFLICT));
    }

    private void insertTenant(long id, String code) {
        jdbcTemplate.update("""
                INSERT INTO tenant (id, tenant_code, tenant_name, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """, id, code, code, timestamp(), timestamp());
    }

    private java.sql.Timestamp timestamp() {
        return java.sql.Timestamp.from(Instant.now());
    }
}
