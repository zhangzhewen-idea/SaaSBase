package com.saasbase.tenant.application.dto;

import com.saasbase.tenant.domain.TenantStatus;
import com.saasbase.tenant.domain.gateway.TenantGateway;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record TenantQuery(
        String tenantCode,
        String tenantName,
        TenantStatus status,
        @Min(1) Integer pageNo,
        @Min(1) @Max(100) Integer pageSize) {
    public TenantQuery {
        pageNo = pageNo == null ? 1 : pageNo;
        pageSize = pageSize == null ? 20 : pageSize;
    }

    public TenantGateway.Query toGatewayQuery() {
        return new TenantGateway.Query(tenantCode, tenantName, status, pageNo, pageSize);
    }
}
