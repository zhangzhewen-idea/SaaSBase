package com.saasbase.iam.infrastructure.persistence;

import com.saasbase.iam.domain.DataScope;
import com.saasbase.iam.domain.Role;
import com.saasbase.iam.domain.RoleStatus;
import com.saasbase.iam.domain.RoleType;

import java.time.LocalDateTime;

public record RoleRecord(Long id, Long tenantId, String roleCode, String roleName, String roleType, String status,
                         String dataScope, Long version, Boolean deleted, LocalDateTime createdAt,
                         LocalDateTime updatedAt, Long createdBy, Long updatedBy, LocalDateTime deletedAt,
                         Long deletedBy) {

    public Role toDomain() {
        return Role.restore(id, tenantId, roleCode, roleName, RoleType.valueOf(roleType),
                RoleStatus.valueOf(status), DataScope.valueOf(dataScope), version);
    }
}
