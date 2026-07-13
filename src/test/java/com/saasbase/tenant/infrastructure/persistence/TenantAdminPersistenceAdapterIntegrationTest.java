package com.saasbase.tenant.infrastructure.persistence;

import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.tenant.domain.gateway.TenantAdminInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class TenantAdminPersistenceAdapterIntegrationTest {
    @Container static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");
    @Autowired TenantAdminInitializer initializer;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @BeforeEach void clean() {
        jdbc.update("DELETE FROM iam_role_permission");
        jdbc.update("DELETE FROM iam_user_role");
        jdbc.update("DELETE FROM iam_role");
        jdbc.update("DELETE FROM iam_user");
        jdbc.update("INSERT IGNORE INTO iam_permission (id, permission_code, permission_name, permission_type, created_at) VALUES (900000000000000106, 'tenant:profile:read', '查看租户资料', 'API', CURRENT_TIMESTAMP(6))");
    }

    @Test
    void initializesAdminWithGeneratedIdsAuditAndOnlyTenantPermissions() {
        initializer.initialize(11L, " admin ", " 管理员 ", "Secret123!", 99L);

        var user = jdbc.queryForMap("SELECT * FROM iam_user WHERE tenant_id=11");
        var role = jdbc.queryForMap("SELECT * FROM iam_role WHERE tenant_id=11");
        assertThat(user.get("id")).isNotNull();
        assertThat(user).containsEntry("username", "admin").containsEntry("display_name", "管理员")
                .containsEntry("status", "ACTIVE").containsEntry("created_by", 99L)
                .containsEntry("updated_by", 99L).containsEntry("deleted", false).containsEntry("version", 0L);
        assertThat(encoder.matches("Secret123!", (String) user.get("password_hash"))).isTrue();
        assertThat(user.get("password_hash")).isNotEqualTo("Secret123!");
        assertThat(user.get("created_at")).isEqualTo(user.get("updated_at"));
        assertThat(role).containsEntry("role_code", "TENANT_ADMIN").containsEntry("role_name", "租户管理员")
                .containsEntry("created_by", 99L).containsEntry("updated_by", 99L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_user_role WHERE tenant_id=11", Long.class)).isOne();
        List<String> codes = jdbc.queryForList("SELECT p.permission_code FROM iam_role_permission rp JOIN iam_permission p ON p.id=rp.permission_id WHERE rp.tenant_id=11", String.class);
        assertThat(codes).contains("tenant:profile:read").allMatch(code -> code.startsWith("tenant:"));
    }

    @Test
    void allowsSameUsernameAcrossTenantsAndMapsOnlyUsernameConflict() {
        initializer.initialize(11L, "admin", "A", "Secret123!", 1L);
        initializer.initialize(12L, "admin", "B", "Secret123!", 1L);
        assertThatThrownBy(() -> initializer.initialize(11L, "admin", "C", "Secret123!", 1L))
                .isInstanceOfSatisfying(BizException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.IAM_USERNAME_CONFLICT));
    }

    @Test
    void missingPermissionTemplateRollsBackDirectInvocation() {
        jdbc.update("DELETE FROM iam_permission WHERE permission_code='tenant:profile:read'");
        assertThatThrownBy(() -> initializer.initialize(13L, "admin", "A", "Secret123!", 1L))
                .isInstanceOfSatisfying(BizException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.IAM_PERMISSION_TEMPLATE_MISSING));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_user WHERE tenant_id=13", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_role WHERE tenant_id=13", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_user_role WHERE tenant_id=13", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_role_permission WHERE tenant_id=13", Long.class)).isZero();
    }

    @Test
    void existingTenantAdminRoleRollsBackNewUserAndKeepsExistingRole() {
        jdbc.update("""
                INSERT INTO iam_role (tenant_id, role_code, role_name, created_at, updated_at, deleted, version)
                VALUES (14, 'TENANT_ADMIN', '预置租户管理员', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0, 0)
                """);

        assertThatThrownBy(() -> initializer.initialize(14L, "admin", "A", "Secret123!", 1L))
                .satisfies(error -> {
                    if (error instanceof BizException bizException) {
                        assertThat(bizException.errorCode()).isNotEqualTo(ErrorCode.IAM_USERNAME_CONFLICT);
                    }
                });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_user WHERE tenant_id=14", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_role WHERE tenant_id=14", Long.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT role_name FROM iam_role WHERE tenant_id=14", String.class))
                .isEqualTo("预置租户管理员");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_user_role WHERE tenant_id=14", Long.class)).isZero();
    }
}
