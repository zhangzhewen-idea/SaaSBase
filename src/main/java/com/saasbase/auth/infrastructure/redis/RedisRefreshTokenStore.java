package com.saasbase.auth.infrastructure.redis;

import com.saasbase.auth.domain.gateway.RefreshTokenStore;
import org.springframework.stereotype.Component;

@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {
    @Override
    public void save(String tokenId, String tokenValue, long expiresAtEpochSecond) {
        throw new UnsupportedOperationException("Redis refresh token store not implemented yet");
    }

    @Override
    public boolean exists(String tokenId) {
        throw new UnsupportedOperationException("Redis refresh token store not implemented yet");
    }

    @Override
    public void revoke(String tokenId) {
        throw new UnsupportedOperationException("Redis refresh token store not implemented yet");
    }
}
