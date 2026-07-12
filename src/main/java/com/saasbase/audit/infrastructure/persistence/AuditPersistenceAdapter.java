package com.saasbase.audit.infrastructure.persistence;

import com.saasbase.audit.domain.AdminOperationAuditEvent;
import com.saasbase.audit.domain.SecurityAuditEvent;
import com.saasbase.audit.domain.gateway.AuditGateway;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class AuditPersistenceAdapter implements AuditGateway {
    private final SecurityAuditMapper securityAuditMapper;
    private final AdminOperationAuditMapper adminOperationAuditMapper;

    public AuditPersistenceAdapter(SecurityAuditMapper securityAuditMapper,
                                   AdminOperationAuditMapper adminOperationAuditMapper) {
        this.securityAuditMapper = securityAuditMapper;
        this.adminOperationAuditMapper = adminOperationAuditMapper;
    }

    @Override
    public void appendSecurityAudit(SecurityAuditEvent event) {
        securityAuditMapper.insert(id(), event);
    }

    @Override
    public void appendAdminOperationAudit(AdminOperationAuditEvent event) {
        adminOperationAuditMapper.insert(id(), event);
    }

    private long id() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
