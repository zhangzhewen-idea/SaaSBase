package com.saasbase.audit.domain.gateway;

import com.saasbase.audit.domain.AdminOperationAuditEvent;
import com.saasbase.audit.domain.SecurityAuditEvent;

public interface AuditGateway {
    void appendSecurityAudit(SecurityAuditEvent event);

    void appendAdminOperationAudit(AdminOperationAuditEvent event);
}
