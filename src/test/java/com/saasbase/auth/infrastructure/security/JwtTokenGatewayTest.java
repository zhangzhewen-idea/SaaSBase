package com.saasbase.auth.infrastructure.security;

import com.saasbase.auth.domain.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenGatewayTest {

    @Test
    void signs_and_parses_access_token() {
        JwtTokenGateway gateway = new JwtTokenGateway("01234567890123456789012345678901", Duration.ofMinutes(15));
        UserPrincipal principal = new UserPrincipal(1001L, 2001L, "alice", Set.of("tenant:user:read"));

        String token = gateway.issueAccessToken(principal);
        UserPrincipal parsed = gateway.parseAccessToken(token);

        assertThat(parsed.userId()).isEqualTo(1001L);
        assertThat(parsed.tenantId()).isEqualTo(2001L);
        assertThat(parsed.permissions()).containsExactly("tenant:user:read");
    }
}
