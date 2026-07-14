package com.saasbase.auth.infrastructure.persistence;

public record UserCredentialRecord(
        Long userId,
        Long tenantId,
        String username,
        String passwordHash,
        String permissions,
        Long sessionVersion,
        Boolean mustChangePassword,
        Boolean superAdmin,
        String status) {
}
