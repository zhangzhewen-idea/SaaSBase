package com.saasbase.iam.domain.gateway;

import java.util.Set;

public interface UserRoleAssignmentGateway {
    void replaceRoles(long tenantId, long userId, Set<Long> roleIds);

    Set<Long> findRoleIds(long tenantId, long userId);

    long countActiveAdministratorsExcludingUser(long tenantId, long userId);

    void lockTenantAdminRole(long tenantId);

    void assertRoleActive(long tenantId, long roleId);
}
