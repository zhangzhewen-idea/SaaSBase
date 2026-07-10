package com.saasbase.auth.application;

import com.saasbase.auth.domain.gateway.TokenRevocationStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenRevocationPolicyTest {

    @Test
    void denies_request_when_revocation_store_is_unavailable() {
        TokenRevocationStore unavailableStore = tokenId -> {
            throw new IllegalStateException("redis unavailable");
        };
        TokenRevocationPolicy policy = new TokenRevocationPolicy(unavailableStore);

        assertThatThrownBy(() -> policy.ensureNotRevoked("token-1"))
                .isInstanceOf(TokenStateUnavailableException.class);
    }

    @Test
    void throws_when_token_is_revoked() {
        TokenRevocationStore revokedStore = tokenId -> true;
        TokenRevocationPolicy policy = new TokenRevocationPolicy(revokedStore);

        assertThatThrownBy(() -> policy.ensureNotRevoked("token-2"))
                .isInstanceOf(RevokedTokenException.class);
    }
}
