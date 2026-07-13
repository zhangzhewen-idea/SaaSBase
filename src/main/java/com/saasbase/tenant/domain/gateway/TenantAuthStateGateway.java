package com.saasbase.tenant.domain.gateway;

import com.saasbase.tenant.domain.TenantAuthState;

public interface TenantAuthStateGateway {
    TenantAuthState requireCurrent(Long tenantId);

    void cache(TenantAuthState tenantAuthState);
}
