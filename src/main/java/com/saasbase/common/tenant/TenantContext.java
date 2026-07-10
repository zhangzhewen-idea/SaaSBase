package com.saasbase.common.tenant;

public record TenantContext(Long tenantId, Long userId, boolean platformRequest) {
}
