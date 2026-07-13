package com.saasbase.iam.domain.gateway;

import com.saasbase.iam.domain.Department;

import java.util.List;
import java.util.Optional;

public interface DepartmentGateway {
    boolean existsByCode(long tenantId, String deptCode);

    Optional<Department> findById(long tenantId, long deptId);

    List<Department> listByTenant(long tenantId);

    void insert(Department department, long operatorId);

    boolean update(Department department, long operatorId);

    boolean delete(long tenantId, long deptId, long version, long operatorId);

    long countChildren(long tenantId, long deptId);

    boolean isDescendant(long tenantId, long ancestorDeptId, long descendantDeptId);

    long depthOf(long tenantId, Long deptId);

    long subtreeDepth(long tenantId, long deptId);
}
