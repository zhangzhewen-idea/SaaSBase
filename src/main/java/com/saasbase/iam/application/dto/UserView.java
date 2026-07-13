package com.saasbase.iam.application.dto;

import com.saasbase.iam.domain.UserStatus;

import java.util.Set;

public record UserView(
        Long id,
        String username,
        String displayName,
        String phone,
        Long primaryDepartmentId,
        UserStatus status,
        long sessionVersion,
        boolean mustChangePassword,
        Set<Long> roleIds) {
}
