package com.saasbase.system.infrastructure.bootstrap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saasbase.tenant.application.TenantApplicationService;
import com.saasbase.tenant.application.dto.CreateTenantRequest;
import com.saasbase.tenant.domain.Tenant;
import com.saasbase.tenant.domain.TenantStatus;
import com.saasbase.tenant.domain.gateway.TenantGateway;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.PreparedStatementCreator;

class PlatformAccountBootstrapRunnerTest {
    @Test
    void run_creates_platform_account_and_initial_tenant_once() {
        PlatformBootstrapProperties properties = new PlatformBootstrapProperties();
        properties.setEnabled(true);
        properties.setPlatformTenantCode("platform");
        properties.setPlatformTenantName("平台管理");
        properties.setPlatformAdminUsername("platform-admin");
        properties.setPlatformAdminDisplayName("平台管理员");
        properties.setPlatformAdminPassword("Platform123!");
        properties.setTenantCode("demo");
        properties.setTenantName("演示租户");
        properties.setTenantAdminUsername("admin");
        properties.setTenantAdminDisplayName("管理员");
        properties.setTenantAdminPassword("Tenant123!");

        TenantGateway tenantGateway = mock(TenantGateway.class);
        TenantApplicationService tenantApplicationService = mock(TenantApplicationService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AtomicLong nextId = new AtomicLong(200L);

        when(jdbcTemplate.query(any(String.class), any(ResultSetExtractor.class), any()))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    Object param = invocation.getArgument(2);
                    if (sql.contains("FROM tenant")) {
                        return null;
                    }
                    if (sql.contains("FROM iam_role")) {
                        return null;
                    }
                    if (sql.contains("FROM iam_user")) {
                        return null;
                    }
                    if (sql.contains("FROM iam_permission")) {
                        return switch (String.valueOf(param)) {
                            case "platform:tenant:create" -> 301L;
                            case "platform:tenant:read" -> 302L;
                            case "platform:tenant:update" -> 303L;
                            case "platform:tenant:enable" -> 304L;
                            case "platform:tenant:disable" -> 305L;
                            default -> null;
                        };
                    }
                    return null;
                });
        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenAnswer(invocation -> {
                    KeyHolder keyHolder = invocation.getArgument(1);
                    ((GeneratedKeyHolder) keyHolder).getKeyList().add(Map.of("id", nextId.getAndIncrement()));
                    return 1;
                });
        when(tenantGateway.insert(any(Tenant.class), isNull()))
                .thenReturn(Tenant.reconstitute(100L, "platform", "平台管理", TenantStatus.ACTIVE, 0L, 0L));
        when(passwordEncoder.encode("Platform123!")).thenReturn("{bcrypt}platform");
        when(tenantGateway.existsByCode("demo")).thenReturn(false);

        PlatformAccountBootstrapRunner runner = new PlatformAccountBootstrapRunner(
                properties, tenantGateway, tenantApplicationService, jdbcTemplate, passwordEncoder);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(tenantGateway).insert(any(Tenant.class), isNull());
        verify(tenantApplicationService).create(any(CreateTenantRequest.class), any(Long.class));
    }

    @Test
    void run_skips_when_disabled() {
        PlatformBootstrapProperties properties = new PlatformBootstrapProperties();
        TenantGateway tenantGateway = mock(TenantGateway.class);
        TenantApplicationService tenantApplicationService = mock(TenantApplicationService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        PlatformAccountBootstrapRunner runner = new PlatformAccountBootstrapRunner(
                properties, tenantGateway, tenantApplicationService, jdbcTemplate, passwordEncoder);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(tenantGateway, never()).insert(any(Tenant.class), any());
        verify(tenantApplicationService, never()).create(any(CreateTenantRequest.class), any());
    }
}
