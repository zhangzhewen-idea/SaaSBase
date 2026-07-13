package com.saasbase.auth.domain;

import java.util.Set;

public record UserPrincipal(Long userId, Long tenantId, String username, Set<String> permissions, long sessionVersion) {
}
