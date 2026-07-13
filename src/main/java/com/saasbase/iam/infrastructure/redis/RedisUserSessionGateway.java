package com.saasbase.iam.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbase.iam.domain.UserAuthState;
import com.saasbase.iam.domain.gateway.UserSessionGateway;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class RedisUserSessionGateway implements UserSessionGateway {
    private static final Duration TTL = Duration.ofSeconds(5);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisUserSessionGateway(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void put(UserAuthState state) {
        try {
            redisTemplate.opsForValue().set(key(state.tenantId(), state.userId()), objectMapper.writeValueAsString(state), TTL);
        } catch (Exception ignored) {
            // fail open for cache writes; callers already persist the source of truth
        }
    }

    @Override
    public Optional<UserAuthState> get(long tenantId, long userId) {
        try {
            String value = redisTemplate.opsForValue().get(key(tenantId, userId));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, UserAuthState.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public UserAuthState getOrLoad(long tenantId, long userId, Supplier<UserAuthState> loader) {
        return get(tenantId, userId).orElseGet(() -> {
            UserAuthState loaded = loader.get();
            try {
                put(loaded);
            } catch (RuntimeException ignored) {
                // ignore cache failures on backfill too
            }
            return loaded;
        });
    }

    private String key(long tenantId, long userId) {
        return "user:auth-state:" + tenantId + ":" + userId;
    }
}
