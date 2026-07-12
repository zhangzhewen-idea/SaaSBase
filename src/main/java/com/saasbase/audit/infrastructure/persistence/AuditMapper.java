package com.saasbase.audit.infrastructure.persistence;

import com.saasbase.audit.domain.AdminOperationAuditEvent;
import com.saasbase.audit.domain.SecurityAuditEvent;
import com.saasbase.audit.domain.gateway.AuditGateway;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

@Repository
public class AuditMapper implements AuditGateway {
    private final JdbcTemplate jdbcTemplate;

    public AuditMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void appendSecurityAudit(SecurityAuditEvent event) {
        jdbcTemplate.update("""
                INSERT INTO security_audit_log
                    (id, tenant_id, user_id, username, event_type, result, client_ip, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id(), event.tenantId(), event.userId(), event.username(), event.eventType(), event.result(),
                event.clientIp(), event.createdAt());
    }

    @Override
    public void appendAdminOperationAudit(AdminOperationAuditEvent event) {
        jdbcTemplate.update("""
                INSERT INTO admin_operation_audit_log
                    (id, tenant_id, user_id, operation_type, resource_type, resource_id, trace_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id(), event.tenantId(), event.userId(), event.operationType(), event.resourceType(),
                event.resourceId(), event.traceId(), event.createdAt());
    }

    private long id() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
