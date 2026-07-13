package com.saasbase.iam;

import com.saasbase.auth.application.AuthApplicationService;
import com.saasbase.auth.application.RefreshRequest;
import com.saasbase.auth.application.dto.LoginRequest;
import com.saasbase.auth.infrastructure.security.JwtAuthenticationFilter;
import com.saasbase.common.tenant.TenantContextHolder;
import com.saasbase.iam.application.UserApplicationService;
import com.saasbase.iam.application.dto.UserCommands.ChangePasswordCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.servlet.FilterChain;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class UserManagementIntegrationTest {
    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AuthApplicationService authApplicationService;

    @Autowired
    UserApplicationService userApplicationService;

    @Autowired
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @BeforeEach
    void clean() {
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
    void clear() {
        TenantContextHolder.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void disabledUserTokensNeverRecoverAfterEnable() throws Exception {
        seedTenant();
        seedAdminRole();
        seedUserRoleAndPermission();
        seedUsers();

        var tokens = createChangePasswordAndLogin();

        userApplicationService.disable(1L, 11L, 12L, 0L);
        assertUnauthorized(tokens.accessToken(), 403);
        assertThatThrownBy(() -> authApplicationService.refresh(new RefreshRequest(tokens.refreshToken())))
                .isInstanceOf(com.saasbase.common.error.BizException.class);

        userApplicationService.enable(1L, 11L, 12L, 1L);
        assertUnauthorized(tokens.accessToken(), 401);
        assertThatThrownBy(() -> authApplicationService.refresh(new RefreshRequest(tokens.refreshToken())))
                .isInstanceOf(com.saasbase.common.error.BizException.class);
    }

    private Tokens createChangePasswordAndLogin() {
        var response = authApplicationService.login(new LoginRequest("tenant-a", "alice", "pass123"));
        return new Tokens(response.accessToken(), response.refreshToken());
    }

    private void assertUnauthorized(String accessToken, int expectedStatus) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);
        request.setRequestURI("/api/v1/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
        };
        jwtAuthenticationFilter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(expectedStatus);
    }

    private void seedTenant() {
        jdbcTemplate.update("INSERT INTO tenant (id, tenant_code, tenant_name, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                1L, "tenant-a", "tenant-a", now(), now());
    }

    private void seedAdminRole() {
        jdbcTemplate.update("INSERT INTO iam_role (id, tenant_id, role_code, role_name, status, created_at, updated_at, deleted) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, 0)",
                100L, 1L, "TENANT_ADMIN", "TENANT_ADMIN", now(), now());
    }

    private void seedUserRoleAndPermission() {
        jdbcTemplate.update("INSERT INTO iam_permission (id, permission_code, permission_name, permission_type, created_at) VALUES (?, ?, ?, 'API', ?)",
                200L, "tenant:user:read", "tenant:user:read", now());
        jdbcTemplate.update("INSERT INTO iam_role_permission (tenant_id, role_id, permission_id) VALUES (?, ?, ?)",
                1L, 100L, 200L);
    }

    private void seedUsers() {
        jdbcTemplate.update("""
                INSERT INTO iam_user
                (id, tenant_id, username, password_hash, display_name, phone, primary_department_id, status,
                 must_change_password, session_version, last_login_at, version, created_at, updated_at, deleted)
                VALUES (?, ?, ?, ?, ?, NULL, NULL, 'ACTIVE', 0, 0, NULL, 0, ?, ?, 0),
                       (?, ?, ?, ?, ?, NULL, NULL, 'ACTIVE', 0, 0, NULL, 0, ?, ?, 0)
                """,
                11L, 1L, "admin", passwordEncoder.encode("admin123"), "admin", now(), now(),
                12L, 1L, "alice", passwordEncoder.encode("pass123"), "alice", now(), now());
        jdbcTemplate.update("INSERT INTO iam_user_role (tenant_id, user_id, role_id) VALUES (?, ?, ?)",
                1L, 11L, 100L);
    }

    private Instant now() {
        return Instant.now();
    }

    private record Tokens(String accessToken, String refreshToken) {
    }
}
