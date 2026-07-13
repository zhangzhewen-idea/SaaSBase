package com.saasbase.iam.domain.gateway;

import com.saasbase.iam.domain.IamUser;

import java.util.List;

public interface DepartmentMemberGateway {
    void assertDepartmentActive(long tenantId, long departmentId);

    List<IamUser> listDirectMembers(long tenantId, long departmentId);

    List<IamUser> listDescendantMembers(long tenantId, long departmentId);

    void transferDepartment(long tenantId, long userId, long departmentId, long version);

    long countDirectMembers(long tenantId, long departmentId);

    boolean existsUser(long tenantId, long userId);
}
