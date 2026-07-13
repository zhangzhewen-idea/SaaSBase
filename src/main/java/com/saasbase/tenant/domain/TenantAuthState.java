package com.saasbase.tenant.domain;

public record TenantAuthState(Long tenantId, TenantStatus status, long sessionVersion) {
    public TenantAuthState {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (sessionVersion < 0) {
            throw new IllegalArgumentException("sessionVersion must not be negative");
        }
    }
}
