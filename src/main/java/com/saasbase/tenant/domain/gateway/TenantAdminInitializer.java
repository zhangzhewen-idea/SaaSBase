package com.saasbase.tenant.domain.gateway;

public interface TenantAdminInitializer {
    void initialize(Long tenantId, String username, String displayName, String rawPassword, Long operatorId);
}
