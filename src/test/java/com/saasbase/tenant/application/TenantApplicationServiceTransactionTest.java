package com.saasbase.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbase.audit.domain.gateway.AuditGateway;
import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.tenant.application.dto.CreateTenantRequest;
import com.saasbase.tenant.domain.TenantAuthState;
import com.saasbase.tenant.domain.TenantStatus;
import com.saasbase.tenant.domain.gateway.TenantAdminInitializer;
import com.saasbase.tenant.domain.gateway.TenantAuthStateGateway;
import com.saasbase.tenant.domain.gateway.TenantGateway;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = {com.saasbase.SaaSBaseApplication.class, TenantApplicationServiceTransactionTest.FailingInitializerConfig.class})
class TenantApplicationServiceTransactionTest {
    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired private TenantApplicationService service;
    @Autowired private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM admin_operation_audit_log");
        jdbcTemplate.update("DELETE FROM security_audit_log");
        jdbcTemplate.update("DELETE FROM iam_role_permission");
        jdbcTemplate.update("DELETE FROM iam_user_role");
        jdbcTemplate.update("DELETE FROM iam_role");
        jdbcTemplate.update("DELETE FROM iam_user");
        jdbcTemplate.update("DELETE FROM tenant");
    }

    @Test
    void create_rolls_back_tenant_user_role_and_audit_when_initializer_fails() {
        jdbcTemplate.update("DELETE FROM iam_permission WHERE permission_code='tenant:profile:read'");

        assertThatThrownBy(() -> service.create(
                new CreateTenantRequest("acme", "Acme", "admin", "管理员", "Secret123456"),
                9L))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(ErrorCode.IAM_PERMISSION_TEMPLATE_MISSING);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tenant WHERE tenant_code='acme'", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam_user WHERE tenant_id=1", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam_role WHERE tenant_id=1", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_operation_audit_log WHERE tenant_id=1", Long.class)).isZero();
    }

    @Configuration
    static class FailingInitializerConfig {
        @Bean
        @Primary
        TenantAdminInitializer failingTenantAdminInitializer(JdbcTemplate jdbcTemplate) {
            return (tenantId, username, displayName, rawPassword, operatorId) -> {
                LocalDateTime now = LocalDateTime.now();
                jdbcTemplate.update("""
                        INSERT INTO iam_user (tenant_id, username, password_hash, display_name, status, created_at, updated_at, deleted, version)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, tenantId, username.trim(), rawPassword, displayName.trim(), "ACTIVE", now, now, false, 0L);
                throw new BizException(ErrorCode.IAM_PERMISSION_TEMPLATE_MISSING);
            };
        }

        @Bean
        @Primary
        TenantAuthStateGateway tenantAuthStateGateway() {
            return new TenantAuthStateGateway() {
                @Override
                public TenantAuthState requireCurrent(Long tenantId) {
                    return new TenantAuthState(tenantId, TenantStatus.ACTIVE, 0L);
                }

                @Override
                public void cache(TenantAuthState tenantAuthState) {
                }
            };
        }
    }
}
