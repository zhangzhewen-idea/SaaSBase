package com.saasbase.auth.infrastructure.redis;

import com.saasbase.auth.domain.gateway.RefreshTokenStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {
    private static final String PREFIX = "saasbase:auth:refresh:";
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current == false or current ~= ARGV[1] then
                return 0
            end
            redis.call('DEL', KEYS[1])
            redis.call('SET', KEYS[2], ARGV[3], 'EX', ARGV[4])
            return 1
            """, Long.class);
    private final StringRedisTemplate redisTemplate;

    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String tokenId, String tokenValue, long expiresAtEpochSecond) {
        long ttl = Math.max(1, expiresAtEpochSecond - java.time.Instant.now().getEpochSecond());
        redisTemplate.opsForValue().set(PREFIX + tokenId, tokenValue, ttl, TimeUnit.SECONDS);
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

    @Override
    public boolean rotate(String oldTokenId, String oldTokenValue, String newTokenId, String newTokenValue,
                          long expiresAtEpochSecond) {
        long ttl = Math.max(1, expiresAtEpochSecond - java.time.Instant.now().getEpochSecond());
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(PREFIX + oldTokenId, PREFIX + newTokenId),
                oldTokenValue,
                newTokenId,
                newTokenValue,
                String.valueOf(ttl));
        return Long.valueOf(1L).equals(result);
    }
}
