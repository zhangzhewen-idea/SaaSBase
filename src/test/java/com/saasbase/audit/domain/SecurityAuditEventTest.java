package com.saasbase.audit.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditEventTest {

    @Test
    void creates_login_failure_event() {
        SecurityAuditEvent event = SecurityAuditEvent.loginFailure(2001L, "alice", "127.0.0.1");

        assertThat(event.tenantId()).isEqualTo(2001L);
        assertThat(event.eventType()).isEqualTo("LOGIN_FAILURE");
        assertThat(event.result()).isEqualTo("FAILURE");
    }
}
