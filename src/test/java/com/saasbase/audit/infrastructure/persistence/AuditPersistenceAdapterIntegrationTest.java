package com.saasbase.audit.infrastructure.persistence;

import com.saasbase.audit.domain.AdminOperationAuditEvent;
import com.saasbase.audit.domain.SecurityAuditEvent;
import com.saasbase.audit.domain.gateway.AuditGateway;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class AuditPersistenceAdapterIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AuditGateway auditGateway;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM security_audit_log");
        jdbcTemplate.update("DELETE FROM admin_operation_audit_log");
    }

    @Test
    void writesSecurityAuditWithAllEventFields() {
        Instant createdAt = Instant.parse("2026-07-13T01:02:03Z");
        SecurityAuditEvent event = new SecurityAuditEvent(7L, 8L, "alice", "LOGIN", "SUCCESS", "127.0.0.1", createdAt);

        auditGateway.appendSecurityAudit(event);

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT tenant_id, user_id, username, event_type, result, client_ip, created_at FROM security_audit_log");
        assertThat(row).containsEntry("tenant_id", 7L).containsEntry("user_id", 8L)
                .containsEntry("username", "alice").containsEntry("event_type", "LOGIN")
                .containsEntry("result", "SUCCESS").containsEntry("client_ip", "127.0.0.1")
                .containsEntry("created_at", localDateTime(createdAt));
    }

    @Test
    void writesAdminOperationAuditWithAllEventFields() {
        Instant createdAt = Instant.parse("2026-07-13T02:03:04Z");
        AdminOperationAuditEvent event = new AdminOperationAuditEvent(7L, 8L, "CREATE", "USER", "11", "trace-1", createdAt);

        auditGateway.appendAdminOperationAudit(event);

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT tenant_id, user_id, operation_type, resource_type, resource_id, trace_id, created_at FROM admin_operation_audit_log");
        assertThat(row).containsEntry("tenant_id", 7L).containsEntry("user_id", 8L)
                .containsEntry("operation_type", "CREATE").containsEntry("resource_type", "USER")
                .containsEntry("resource_id", "11").containsEntry("trace_id", "trace-1")
                .containsEntry("created_at", localDateTime(createdAt));
    }

    private LocalDateTime localDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
