package com.saasbase.iam.domain;

import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;

import java.util.Objects;

public final class Department {
    public static final String ROOT_CODE = "ROOT";

    private final Long id;
    private final Long tenantId;
    private Long parentId;
    private final String deptCode;
    private String deptName;
    private long sortOrder;
    private DepartmentStatus status;
    private final long version;

    public Department(Long id, Long tenantId, Long parentId, String deptCode, String deptName,
                      long sortOrder, DepartmentStatus status, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.parentId = parentId;
        this.deptCode = requireText(deptCode, "deptCode");
        this.deptName = requireText(deptName, "deptName");
        this.sortOrder = sortOrder;
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
    }

    public void rename(String deptName, long sortOrder) {
        this.deptName = requireText(deptName, "deptName");
        this.sortOrder = sortOrder;
    }

    public void moveTo(Long parentId) {
        this.parentId = parentId;
    }

    public void disable() {
        if (status != DepartmentStatus.ACTIVE) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_STATUS_CONFLICT);
        }
        status = DepartmentStatus.DISABLED;
    }

    public void enable() {
        if (status != DepartmentStatus.DISABLED) {
            throw new BizException(ErrorCode.IAM_DEPARTMENT_STATUS_CONFLICT);
        }
        status = DepartmentStatus.ACTIVE;
    }

    public Long id() {
        return id;
    }

    public Long tenantId() {
        return tenantId;
    }

    public Long parentId() {
        return parentId;
    }

    public String deptCode() {
        return deptCode;
    }

    public String deptName() {
        return deptName;
    }

    public long sortOrder() {
        return sortOrder;
    }

    public DepartmentStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
