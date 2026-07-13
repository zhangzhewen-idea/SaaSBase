package com.saasbase.tenant.infrastructure.persistence;

import com.saasbase.tenant.domain.TenantStatus;

import java.time.LocalDateTime;

public record TenantRecord(
        Long id,
        String tenantCode,
        String tenantName,
        TenantStatus status,
        Long sessionVersion,
        LocalDateTime createdAt,
        Long createdBy,
        LocalDateTime updatedAt,
        Long updatedBy,
        Boolean deleted,
        Long version) {
}
