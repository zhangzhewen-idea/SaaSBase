package com.saasbase.iam.application.dto;

import com.saasbase.iam.domain.DataScope;
import com.saasbase.iam.domain.RoleStatus;
import com.saasbase.iam.domain.RoleType;

public final class RoleCommands {
    private RoleCommands() {
    }

    public record CreateRoleCommand(String roleCode, String roleName, DataScope dataScope) {
    }

    public record UpdateRoleCommand(String roleName, DataScope dataScope, RoleStatus status) {
    }

    public record RolePageQuery(String keyword, RoleStatus status, RoleType type, long pageNo, long pageSize) {
    }
}
