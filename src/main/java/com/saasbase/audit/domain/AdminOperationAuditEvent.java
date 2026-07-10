package com.saasbase.audit.domain;

import java.time.Instant;

public record AdminOperationAuditEvent(
        Long tenantId,
        Long userId,
        String operationType,
        String resourceType,
        String resourceId,
        String traceId,
        Instant createdAt) {
}
