package com.saasbase.iam.infrastructure.persistence;

import com.saasbase.audit.domain.gateway.AuditGateway;
import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.iam.application.DepartmentApplicationService;
import com.saasbase.iam.application.dto.DepartmentCommands.CreateDepartmentCommand;
import com.saasbase.iam.application.dto.DepartmentCommands.MoveDepartmentCommand;
import com.saasbase.iam.application.dto.DepartmentCommands.ToggleDepartmentCommand;
import com.saasbase.iam.domain.IamUser;
import com.saasbase.iam.domain.DepartmentStatus;
import com.saasbase.iam.domain.gateway.DepartmentGateway;
import com.saasbase.iam.domain.gateway.DepartmentMemberGateway;
import com.saasbase.tenant.domain.gateway.TenantDepartmentInitializer;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class DepartmentPersistenceAdapterIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DepartmentApplicationService departmentApplicationService;

    @Autowired
    DepartmentGateway departmentGateway;

    @Autowired
    DepartmentMemberGateway departmentMemberGateway;

    @Autowired
    TenantDepartmentInitializer tenantDepartmentInitializer;

    @Autowired
    AuditGateway auditGateway;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM iam_user_role");
        jdbcTemplate.update("DELETE FROM iam_role_permission");
        jdbcTemplate.update("DELETE FROM iam_permission");
        jdbcTemplate.update("DELETE FROM iam_user");
        jdbcTemplate.update("DELETE FROM iam_role");
        jdbcTemplate.update("DELETE FROM iam_dept");
        jdbcTemplate.update("DELETE FROM tenant");
    }

    @AfterEach
    void clear() {
    }

    @Test
    void rootDepartmentIsInitializedWithTenantName() {
        insertTenant(1L, "tenant-a", "Tenant A");

        tenantDepartmentInitializer.initializeRootDepartment(1L, "Tenant A", 99L);

        assertThat(departmentGateway.listByTenant(1L))
                .extracting(DepartmentGatewayTestSupport::deptCode)
                .containsExactly("ROOT");
        assertThat(departmentGateway.findById(1L, rootDeptId())).isPresent();
    }

    @Test
    void createMoveDisableDeleteAndMemberQueriesFollowRules() {
        insertTenant(1L, "tenant-a", "Tenant A");
        tenantDepartmentInitializer.initializeRootDepartment(1L, "Tenant A", 99L);
        long rootId = rootDeptId();
        departmentApplicationService.create(1L, 99L, new CreateDepartmentCommand(rootId, "FIN", "Finance", 10));
        long finId = departmentGateway.listByTenant(1L).stream().filter(d -> "FIN".equals(d.deptCode())).findFirst().orElseThrow().id();
        departmentApplicationService.create(1L, 99L, new CreateDepartmentCommand(finId, "FIN-AP", "AP", 20));
        long apId = departmentGateway.listByTenant(1L).stream().filter(d -> "FIN-AP".equals(d.deptCode())).findFirst().orElseThrow().id();

        insertUser(11L, 1L, "alice", finId, "ACTIVE");
        insertUser(12L, 1L, "bob", apId, "ACTIVE");

        assertThat(departmentMemberGateway.listDirectMembers(1L, finId)).extracting(IamUser::username).containsExactly("alice");
        assertThat(departmentMemberGateway.listDescendantMembers(1L, finId)).extracting(IamUser::username).containsExactlyInAnyOrder("alice", "bob");

        departmentApplicationService.move(1L, 99L, apId, new MoveDepartmentCommand(rootId, 0L));
        departmentApplicationService.disable(1L, 99L, finId, new ToggleDepartmentCommand(0L));
        assertThatThrownBy(() -> departmentApplicationService.create(1L, 99L, new CreateDepartmentCommand(finId, "FIN-AR", "AR", 30)))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(ErrorCode.IAM_DEPARTMENT_NOT_FOUND);

        assertThatThrownBy(() -> departmentApplicationService.delete(1L, 99L, finId, new ToggleDepartmentCommand(0L)))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(ErrorCode.IAM_DEPARTMENT_NOT_EMPTY);
    }

    @Test
    void rejectsCyclesAndDepthOverflow() {
        insertTenant(1L, "tenant-a", "Tenant A");
        tenantDepartmentInitializer.initializeRootDepartment(1L, "Tenant A", 99L);
        long parentId = rootDeptId();
        long lastId = parentId;
        for (int i = 0; i < 9; i++) {
            long index = i;
            departmentApplicationService.create(1L, 99L, new CreateDepartmentCommand(lastId, "D" + index, "D" + index, index));
            lastId = departmentGateway.listByTenant(1L).stream()
                    .filter(d -> ("D" + index).equals(d.deptCode()))
                    .findFirst().orElseThrow().id();
        }
        long depthNineLeaf = lastId;
        assertThatThrownBy(() -> departmentApplicationService.create(1L, 99L, new CreateDepartmentCommand(depthNineLeaf, "TOO-DEEP", "Too Deep", 1)))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(ErrorCode.IAM_DEPARTMENT_DEPTH_LIMIT_EXCEEDED);

        long firstChild = departmentGateway.listByTenant(1L).stream().filter(d -> "D0".equals(d.deptCode())).findFirst().orElseThrow().id();
        assertThatThrownBy(() -> departmentApplicationService.move(1L, 99L, parentId, new MoveDepartmentCommand(firstChild, 0L)))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(ErrorCode.IAM_DEPARTMENT_ROOT_NOT_ALLOWED);
    }

    private void insertTenant(long id, String code, String name) {
        jdbcTemplate.update("INSERT INTO tenant (id, tenant_code, tenant_name, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                id, code, name, now(), now());
    }

    private void insertUser(long id, long tenantId, String username, long departmentId, String status) {
        jdbcTemplate.update("""
                INSERT INTO iam_user
                (id, tenant_id, username, password_hash, display_name, phone, primary_department_id, status,
                 must_change_password, session_version, last_login_at, version, created_at, updated_at, deleted)
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?, 0, 0, NULL, 0, ?, ?, 0)
                """,
                id, tenantId, username, "hash-" + username, username, departmentId, status, now(), now());
    }

    private long rootDeptId() {
        return jdbcTemplate.queryForObject("SELECT id FROM iam_dept WHERE dept_code = 'ROOT' AND deleted = 0", Long.class);
    }

    private Instant now() {
        return Instant.now();
    }

    private static final class DepartmentGatewayTestSupport {
        private static String deptCode(com.saasbase.iam.domain.Department department) {
            return department.deptCode();
        }
    }
}
