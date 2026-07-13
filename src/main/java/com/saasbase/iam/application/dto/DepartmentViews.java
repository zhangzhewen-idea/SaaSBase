package com.saasbase.iam.application.dto;

import com.saasbase.iam.domain.DepartmentStatus;

import java.util.List;

public final class DepartmentViews {
    private DepartmentViews() {
    }

    public record DepartmentTreeView(
            Long id,
            Long parentId,
            String deptCode,
            String deptName,
            long sortOrder,
            DepartmentStatus status,
            List<DepartmentTreeView> children) {
    }
}
