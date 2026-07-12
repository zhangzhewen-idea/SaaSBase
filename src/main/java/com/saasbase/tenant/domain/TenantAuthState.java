package com.saasbase.tenant.domain;

public record TenantAuthState(Long tenantId, TenantStatus status, long sessionVersion) {
}
