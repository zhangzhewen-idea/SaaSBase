package com.saasbase.auth.infrastructure.redis;

import com.saasbase.auth.domain.gateway.RefreshTokenStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {
    private static final String PREFIX = "saasbase:auth:refresh:";
    private final StringRedisTemplate redisTemplate;

    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String tokenId, String tokenValue, long expiresAtEpochSecond) {
        long ttl = Math.max(1, expiresAtEpochSecond - java.time.Instant.now().getEpochSecond());
        redisTemplate.opsForValue().set(PREFIX + tokenId, tokenValue, Duration.ofSeconds(ttl));
    }

    @Override
    public String find(String tokenId) {
        return redisTemplate.opsForValue().get(PREFIX + tokenId);
    }

    @Override
    public boolean exists(String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + tokenId));
    }

    @Override
    public void revoke(String tokenId) {
        redisTemplate.delete(PREFIX + tokenId);
    }
}
