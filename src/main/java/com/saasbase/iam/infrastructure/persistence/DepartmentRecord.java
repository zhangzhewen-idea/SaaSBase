package com.saasbase.iam.infrastructure.persistence;

import com.saasbase.iam.domain.DepartmentStatus;

public record DepartmentRecord(
        Long id,
        Long tenantId,
        Long parentId,
        String deptCode,
        String deptName,
        long sortOrder,
        DepartmentStatus status,
        Long version,
        Boolean deleted) {
}
