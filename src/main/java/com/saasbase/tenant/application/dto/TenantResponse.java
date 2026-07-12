package com.saasbase.tenant.application.dto;

import com.saasbase.tenant.domain.Tenant;
import com.saasbase.tenant.domain.TenantStatus;

public record TenantResponse(
        Long id,
        String tenantCode,
        String tenantName,
        TenantStatus status,
        long sessionVersion,
        long version) {
    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.id(),
                tenant.tenantCode(),
                tenant.tenantName(),
                tenant.status(),
                tenant.sessionVersion(),
                tenant.version());
    }
}
