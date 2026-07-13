package com.saasbase.iam.domain.gateway;

public interface DepartmentReferenceGateway {
    void assertDepartmentActive(long tenantId, long departmentId);
}
