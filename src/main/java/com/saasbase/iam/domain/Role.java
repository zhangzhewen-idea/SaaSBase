package com.saasbase.iam.domain;

import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;

import java.util.Objects;

public final class Role {
    private final Long id;
    private final Long tenantId;
    private String roleCode;
    private String roleName;
    private final RoleType roleType;
    private RoleStatus status;
    private boolean deleted;
    private DataScope dataScope;
    private final Long version;

    private Role(Long id, Long tenantId, String roleCode, String roleName, RoleType roleType,
                 RoleStatus status, DataScope dataScope, Long version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.roleCode = requireText(roleCode, "roleCode");
        this.roleName = requireText(roleName, "roleName");
        this.roleType = Objects.requireNonNull(roleType, "roleType must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.deleted = false;
        this.dataScope = Objects.requireNonNull(dataScope, "dataScope must not be null");
        this.version = Objects.requireNonNull(version, "version must not be null");
    }

    public static Role create(Long id, Long tenantId, String roleCode, String roleName, DataScope dataScope) {
        return new Role(id, tenantId, roleCode, roleName, RoleType.CUSTOM, RoleStatus.ACTIVE, dataScope, 0L);
    }

    public static Role restore(Long id, Long tenantId, String roleCode, String roleName, RoleType roleType,
                               RoleStatus status, DataScope dataScope, Long version) {
        return new Role(id, tenantId, roleCode, roleName, roleType, status, dataScope, version);
    }

    public Long id() {
        return id;
    }

    public Long tenantId() {
        return tenantId;
    }

    public String roleCode() {
        return roleCode;
    }

    public String roleName() {
        return roleName;
    }

    public RoleType roleType() {
        return roleType;
    }

    public RoleStatus status() {
        return status;
    }

    public boolean deleted() {
        return deleted;
    }

    public DataScope dataScope() {
        return dataScope;
    }

    public Long version() {
        return version;
    }

    public void rename(String roleName) {
        this.roleName = requireText(roleName, "roleName");
    }

    public void changeDataScope(DataScope dataScope) {
        this.dataScope = Objects.requireNonNull(dataScope, "dataScope must not be null");
    }

    public void enable() {
        ensureMutableStatusChange();
        this.status = RoleStatus.ACTIVE;
    }

    public void disable() {
        ensureMutableStatusChange();
        this.status = RoleStatus.DISABLED;
    }

    public void delete() {
        ensureMutableStatusChange();
        this.status = RoleStatus.DISABLED;
        this.deleted = true;
    }

    public void changeCode(String roleCode) {
        String nextRoleCode = requireText(roleCode, "roleCode");
        if (!this.roleCode.equals(nextRoleCode)) {
            ensureCodeMutable();
            this.roleCode = nextRoleCode;
        }
    }

    private void ensureMutableStatusChange() {
        ensureCodeMutable();
    }

    private void ensureCodeMutable() {
        if (isTenantAdmin()) {
            throw new BizException(ErrorCode.IAM_BUILT_IN_ROLE_PROTECTED);
        }
    }

    private boolean isTenantAdmin() {
        return roleType == RoleType.BUILT_IN && "TENANT_ADMIN".equals(roleCode);
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
