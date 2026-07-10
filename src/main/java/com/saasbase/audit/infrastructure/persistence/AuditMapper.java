package com.saasbase.audit.infrastructure.persistence;

import com.saasbase.audit.domain.AdminOperationAuditEvent;
import com.saasbase.audit.domain.SecurityAuditEvent;
import com.saasbase.audit.domain.gateway.AuditGateway;
import org.springframework.stereotype.Repository;

@Repository
public class AuditMapper implements AuditGateway {
    @Override
    public void appendSecurityAudit(SecurityAuditEvent event) {
        throw new UnsupportedOperationException("audit persistence not implemented yet");
    }

    @Override
    public void appendAdminOperationAudit(AdminOperationAuditEvent event) {
        throw new UnsupportedOperationException("audit persistence not implemented yet");
    }
}
