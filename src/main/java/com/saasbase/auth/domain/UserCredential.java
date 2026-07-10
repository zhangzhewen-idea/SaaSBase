package com.saasbase.auth.domain;

import java.util.Set;

public record UserCredential(
        Long userId,
        Long tenantId,
        String username,
        String passwordHash,
        Set<String> permissions) {
}
