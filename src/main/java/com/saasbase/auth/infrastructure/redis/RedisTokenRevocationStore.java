package com.saasbase.auth.infrastructure.redis;

import com.saasbase.auth.domain.gateway.TokenRevocationStore;
import org.springframework.stereotype.Component;

@Component
public class RedisTokenRevocationStore implements TokenRevocationStore {
    @Override
    public boolean isRevoked(String tokenId) {
        throw new UnsupportedOperationException("Redis token revocation store not implemented yet");
    }
}
