package com.saasbase.auth.infrastructure.redis;

import com.saasbase.auth.domain.gateway.TokenRevocationStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisTokenRevocationStore implements TokenRevocationStore {
    private static final String PREFIX = "saasbase:auth:revoked:";
    private final StringRedisTemplate redisTemplate;

    public RedisTokenRevocationStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void revoke(String tokenId, long expiresAtEpochSecond) {
        long ttl = Math.max(1, expiresAtEpochSecond - java.time.Instant.now().getEpochSecond());
        redisTemplate.opsForValue().set(PREFIX + tokenId, "1", ttl, TimeUnit.SECONDS);
    }

    @Override
    public boolean isRevoked(String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + tokenId));
    }
}
