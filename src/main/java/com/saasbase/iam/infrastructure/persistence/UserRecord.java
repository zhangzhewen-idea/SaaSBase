package com.saasbase.iam.infrastructure.persistence;

public record UserRecord(
        Long id,
        Long tenantId,
        String username,
        String passwordHash,
        String displayName,
        String phone,
        Long primaryDepartmentId,
        String status,
        Boolean mustChangePassword,
        Long sessionVersion,
        java.time.LocalDateTime lastLoginAt,
        Long version,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime updatedAt,
        Boolean deleted) {
}
