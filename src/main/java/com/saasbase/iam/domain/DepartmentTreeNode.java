package com.saasbase.iam.domain;

import java.util.List;

public record DepartmentTreeNode(
        Long id,
        Long parentId,
        String deptCode,
        String deptName,
        long sortOrder,
        DepartmentStatus status,
        List<DepartmentTreeNode> children) {
}
