package com.saasbase.tenant.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.tenant.domain.TenantAuthState;
import com.saasbase.tenant.domain.TenantStatus;
import com.saasbase.tenant.domain.gateway.TenantAuthStateGateway;
import com.saasbase.tenant.domain.gateway.TenantGateway;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisTenantAuthStateGateway implements TenantAuthStateGateway {
    private static final Logger log = LoggerFactory.getLogger(RedisTenantAuthStateGateway.class);
    private static final String KEY_PREFIX = "saasbase:tenant:auth-state:";
    private static final Duration TTL = Duration.ofSeconds(5);

    private final StringRedisTemplate redisTemplate;
    private final TenantGateway tenantGateway;
    private final ObjectMapper objectMapper;

    public RedisTenantAuthStateGateway(
            StringRedisTemplate redisTemplate, TenantGateway tenantGateway, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.tenantGateway = tenantGateway;
        this.objectMapper = objectMapper;
    }

    @Override
    public TenantAuthState requireCurrent(Long tenantId) {
        requirePositiveTenantId(tenantId);
        TenantAuthState cached = readCached(tenantId);
        if (cached != null) {
            return cached;
        }

        TenantAuthState authoritative = tenantGateway.findById(tenantId)
                .orElseThrow(() -> new BizException(ErrorCode.TENANT_NOT_FOUND))
                .authState();
        try {
            cache(authoritative);
        } catch (RuntimeException exception) {
            log.warn("Tenant auth-state cache backfill failed: tenantId={}, exceptionType={}",
                    tenantId, exception.getClass().getName());
        }
        return authoritative;
    }

    @Override
    public void cache(TenantAuthState tenantAuthState) {
        if (tenantAuthState == null) {
            throw new IllegalArgumentException("tenantAuthState must not be null");
        }
        requirePositiveTenantId(tenantAuthState.tenantId());
        CacheValue value = new CacheValue(tenantAuthState.status(), tenantAuthState.sessionVersion());
        try {
            redisTemplate.opsForValue().set(key(tenantAuthState.tenantId()), objectMapper.writeValueAsString(value), TTL);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize tenant auth state", exception);
        }
    }

    private TenantAuthState readCached(Long tenantId) {
        final String serialized;
        try {
            serialized = redisTemplate.opsForValue().get(key(tenantId));
        } catch (DataAccessException exception) {
            log.warn("Tenant auth-state cache read failed: tenantId={}, exceptionType={}",
                    tenantId, exception.getClass().getName());
            return null;
        }
        if (serialized == null) {
            return null;
        }
        try {
            CacheValue value = objectMapper.readValue(serialized, CacheValue.class);
            if (value.status() == null || value.sessionVersion() < 0) {
                return null;
            }
            return new TenantAuthState(tenantId, value.status(), value.sessionVersion());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            log.warn("Tenant auth-state cache value invalid: tenantId={}, exceptionType={}",
                    tenantId, exception.getClass().getName());
            return null;
        }
    }

    private static void requirePositiveTenantId(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
    }

    private static String key(Long tenantId) {
        return KEY_PREFIX + tenantId;
    }

    private record CacheValue(TenantStatus status, long sessionVersion) {}
}
