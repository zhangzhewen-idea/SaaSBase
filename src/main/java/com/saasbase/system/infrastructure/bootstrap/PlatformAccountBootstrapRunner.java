package com.saasbase.system.infrastructure.bootstrap;

import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.tenant.application.TenantApplicationService;
import com.saasbase.tenant.application.dto.CreateTenantRequest;
import com.saasbase.tenant.domain.Tenant;
import com.saasbase.tenant.domain.gateway.TenantGateway;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@EnableConfigurationProperties(PlatformBootstrapProperties.class)
public class PlatformAccountBootstrapRunner implements ApplicationRunner {
    private static final String PLATFORM_ROOT_DEPT_CODE = "PLATFORM_ROOT";
    private static final String PLATFORM_ROOT_DEPT_NAME = "平台根部门";

    private final PlatformBootstrapProperties properties;
    private final TenantGateway tenantGateway;
    private final TenantApplicationService tenantApplicationService;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public PlatformAccountBootstrapRunner(
            PlatformBootstrapProperties properties,
            TenantGateway tenantGateway,
            TenantApplicationService tenantApplicationService,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.tenantGateway = tenantGateway;
        this.tenantApplicationService = tenantApplicationService;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        Long platformTenantId = ensurePlatformTenant();
        Long platformAdminId = ensurePlatformAdmin(platformTenantId);
        Long platformRootDepartmentId = ensurePlatformRootDepartment(platformTenantId, platformAdminId);
        assignUserDepartment(platformTenantId, platformAdminId, platformRootDepartmentId);
        ensurePlatformDemoAccounts(platformTenantId, platformRootDepartmentId);
        ensureInitialTenant(platformAdminId);
    }

    private Long ensurePlatformTenant() {
        return Optional.ofNullable(jdbcTemplate.query("""
                        SELECT id
                          FROM tenant
                         WHERE tenant_code = ?
                           AND deleted = 0
                        """,
                rs -> rs.next() ? rs.getLong("id") : null,
                properties.getPlatformTenantCode()))
                .orElseGet(() -> tenantGateway.insert(
                        Tenant.create(properties.getPlatformTenantCode(), properties.getPlatformTenantName()),
                        null).id());
    }

    private Long ensurePlatformAdmin(Long platformTenantId) {
        Long existingUserId = findUserId(platformTenantId, properties.getPlatformAdminUsername());
        if (existingUserId != null) {
            return existingUserId;
        }
        Long roleId = ensurePlatformRole(platformTenantId);
        Long userId = insertUser(platformTenantId,
                properties.getPlatformAdminUsername(),
                properties.getPlatformAdminDisplayName(),
                properties.getPlatformAdminPassword());
        replaceUserRole(platformTenantId, userId, roleId);
        replaceRolePermissions(platformTenantId, roleId, findAllPermissionCodes());
        return userId;
    }

    private Long ensurePlatformRootDepartment(Long platformTenantId, Long operatorId) {
        Long existingDepartmentId = findDepartmentId(platformTenantId, PLATFORM_ROOT_DEPT_CODE);
        if (existingDepartmentId != null) {
            return existingDepartmentId;
        }
        Long departmentId = nextId();
        insertDepartment(platformTenantId, departmentId, PLATFORM_ROOT_DEPT_CODE, PLATFORM_ROOT_DEPT_NAME, operatorId);
        assignUserDepartment(platformTenantId, operatorId, departmentId);
        return departmentId;
    }

    private void ensurePlatformDemoAccounts(Long platformTenantId, Long platformRootDepartmentId) {
        Long roleId = ensurePlatformRole(platformTenantId);
        replaceRolePermissions(platformTenantId, roleId, findAllPermissionCodes());
        for (DemoAccount account : demoAccounts()) {
            Long userId = findUserId(platformTenantId, account.username());
            if (userId != null) {
                assignUserDepartment(platformTenantId, userId, platformRootDepartmentId);
                continue;
            }
            userId = insertUser(platformTenantId, account.username(), account.displayName(), account.password());
            assignUserDepartment(platformTenantId, userId, platformRootDepartmentId);
            replaceUserRole(platformTenantId, userId, roleId);
        }
    }

    private void ensureInitialTenant(Long operatorId) {
        if (tenantGateway.existsByCode(properties.getTenantCode())) {
            return;
        }
        tenantApplicationService.create(new CreateTenantRequest(
                properties.getTenantCode(),
                properties.getTenantName(),
                properties.getTenantAdminUsername(),
                properties.getTenantAdminDisplayName(),
                properties.getTenantAdminPassword()), operatorId);
    }

    private Long ensurePlatformRole(Long platformTenantId) {
        return Optional.ofNullable(jdbcTemplate.query("""
                        SELECT id
                          FROM iam_role
                         WHERE tenant_id = ?
                           AND role_code = 'PLATFORM_ADMIN'
                           AND deleted = 0
                        """,
                rs -> rs.next() ? rs.getLong("id") : null,
                platformTenantId))
                .orElseGet(() -> insertRole(platformTenantId, "PLATFORM_ADMIN", "平台管理员"));
    }

    private Long insertUser(long tenantId, String username, String displayName, String rawPassword) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO iam_user
                    (tenant_id, username, password_hash, display_name, status, must_change_password, session_version,
                     created_at, updated_at, deleted, version)
                    VALUES (?, ?, ?, ?, 'ACTIVE', 0, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0, 0)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, tenantId);
            statement.setString(2, username);
            statement.setString(3, passwordEncoder.encode(rawPassword));
            statement.setString(4, displayName);
            return statement;
        }, keyHolder);
        return requireGeneratedId(keyHolder, "iam_user");
    }

    private Long insertRole(long tenantId, String roleCode, String roleName) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO iam_role
                    (tenant_id, role_code, role_name, role_type, status, data_scope, created_at, updated_at, deleted, version)
                    VALUES (?, ?, ?, 'BUILT_IN', 'ACTIVE', 'ALL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0, 0)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, tenantId);
            statement.setString(2, roleCode);
            statement.setString(3, roleName);
            return statement;
        }, keyHolder);
        return requireGeneratedId(keyHolder, "iam_role");
    }

    private void insertDepartment(long tenantId, long departmentId, String deptCode, String deptName, Long operatorId) {
        jdbcTemplate.update("""
                        INSERT INTO iam_dept
                        (id, tenant_id, parent_id, dept_code, dept_name, sort_order, status,
                         created_at, created_by, updated_at, updated_by, deleted, version)
                        VALUES (?, ?, NULL, ?, ?, 0, 'ACTIVE', CURRENT_TIMESTAMP(6), ?, CURRENT_TIMESTAMP(6), ?, 0, 0)
                        """,
                departmentId, tenantId, deptCode, deptName, operatorId, operatorId);
    }

    private void replaceUserRole(long tenantId, long userId, long roleId) {
        jdbcTemplate.update("DELETE FROM iam_user_role WHERE tenant_id = ? AND user_id = ?", tenantId, userId);
        jdbcTemplate.update("INSERT INTO iam_user_role (tenant_id, user_id, role_id) VALUES (?, ?, ?)", tenantId, userId, roleId);
    }

    private void assignUserDepartment(long tenantId, long userId, Long departmentId) {
        jdbcTemplate.update("""
                        UPDATE iam_user
                           SET primary_department_id = ?,
                               updated_at = CURRENT_TIMESTAMP(6)
                         WHERE tenant_id = ?
                           AND id = ?
                           AND deleted = 0
                        """,
                departmentId, tenantId, userId);
    }

    private void replaceRolePermissions(long tenantId, long roleId, Set<String> permissionCodes) {
        if (permissionCodes.isEmpty()) {
            throw new BizException(ErrorCode.IAM_PERMISSION_TEMPLATE_MISSING);
        }
        jdbcTemplate.update("DELETE FROM iam_role_permission WHERE tenant_id = ? AND role_id = ?", tenantId, roleId);
        for (String permissionCode : permissionCodes) {
            Long permissionId = jdbcTemplate.query("""
                            SELECT id
                              FROM iam_permission
                             WHERE permission_code = ?
                            """,
                    rs -> rs.next() ? rs.getLong("id") : null,
                    permissionCode);
            if (permissionId == null) {
                throw new IllegalStateException("Missing platform permission template: " + permissionCode);
            }
            jdbcTemplate.update("INSERT INTO iam_role_permission (tenant_id, role_id, permission_id) VALUES (?, ?, ?)",
                    tenantId, roleId, permissionId);
        }
    }

    private Set<String> findAllPermissionCodes() {
        return new java.util.LinkedHashSet<>(jdbcTemplate.queryForList("""
                        SELECT permission_code
                          FROM iam_permission
                         ORDER BY id
                        """,
                String.class));
    }

    private Long findUserId(long tenantId, String username) {
        return jdbcTemplate.query("""
                        SELECT id
                          FROM iam_user
                         WHERE tenant_id = ?
                           AND username = ?
                           AND deleted = 0
                        """,
                rs -> rs.next() ? rs.getLong("id") : null,
                tenantId, username);
    }

    private Long findDepartmentId(long tenantId, String deptCode) {
        return jdbcTemplate.query("""
                        SELECT id
                          FROM iam_dept
                         WHERE tenant_id = ?
                           AND dept_code = ?
                           AND deleted = 0
                        """,
                rs -> rs.next() ? rs.getLong("id") : null,
                tenantId, deptCode);
    }

    private Long requireGeneratedId(KeyHolder keyHolder, String tableName) {
        Number key = keyHolder.getKey();
        if (key == null || key.longValue() <= 0) {
            throw new IllegalStateException("Database did not generate a valid id for " + tableName);
        }
        return key.longValue();
    }

    private List<DemoAccount> demoAccounts() {
        return List.of(
                new DemoAccount("platform-demo-1", "平台示例账号1", "PlatformDemo123!"),
                new DemoAccount("platform-demo-2", "平台示例账号2", "PlatformDemo123!"),
                new DemoAccount("platform-demo-3", "平台示例账号3", "PlatformDemo123!"),
                new DemoAccount("platform-demo-4", "平台示例账号4", "PlatformDemo123!"),
                new DemoAccount("platform-demo-5", "平台示例账号5", "PlatformDemo123!"));
    }

    private long nextId() {
        return System.currentTimeMillis();
    }

    private record DemoAccount(String username, String displayName, String password) {
    }
}
