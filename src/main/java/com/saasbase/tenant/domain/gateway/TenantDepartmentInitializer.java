package com.saasbase.tenant.domain.gateway;

public interface TenantDepartmentInitializer {
    void initializeRootDepartment(Long tenantId, String departmentName, Long operatorId);
}
