package com.saasbase.iam.application.dto;

import com.saasbase.iam.domain.DataScope;
import com.saasbase.iam.domain.RoleStatus;
import com.saasbase.iam.domain.RoleType;

public record RoleView(Long id, Long tenantId, String roleCode, String roleName, RoleType roleType,
                       RoleStatus status, DataScope dataScope, Long version, boolean deleted) {
}
