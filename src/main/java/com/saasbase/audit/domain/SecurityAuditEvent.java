package com.saasbase.audit.domain;

import java.time.Instant;

public record SecurityAuditEvent(
        Long tenantId,
        Long userId,
        String username,
        String eventType,
        String result,
        String clientIp,
        Instant createdAt) {

    public static SecurityAuditEvent loginFailure(Long tenantId, String username, String clientIp) {
        return new SecurityAuditEvent(tenantId, null, username, "LOGIN_FAILURE", "FAILURE", clientIp, Instant.now());
    }

    public static SecurityAuditEvent loginSuccess(Long tenantId, Long userId, String username, String clientIp) {
        return new SecurityAuditEvent(tenantId, userId, username, "LOGIN", "SUCCESS", clientIp, Instant.now());
    }
}
