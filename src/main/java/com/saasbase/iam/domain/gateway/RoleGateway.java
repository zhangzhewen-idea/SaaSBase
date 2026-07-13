package com.saasbase.iam.domain.gateway;

import com.saasbase.common.api.PageResponse;
import com.saasbase.iam.domain.Role;
import com.saasbase.iam.domain.RoleStatus;
import com.saasbase.iam.domain.RoleType;

import java.util.Optional;

public interface RoleGateway {
    Optional<Role> findById(long tenantId, long roleId);

    boolean existsByCode(long tenantId, String roleCode);

    PageResponse<Role> page(long tenantId, String keyword, RoleStatus status, RoleType type, long pageNo, long pageSize);

    void insert(Role role);

    boolean update(Role role, long expectedVersion);

    void deleteRelationsAndSoftDelete(long tenantId, long roleId, long operatorId);
}
