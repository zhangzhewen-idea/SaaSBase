package com.saasbase.tenant.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.tenant.domain.Tenant;
import com.saasbase.tenant.domain.TenantAuthState;
import com.saasbase.tenant.domain.TenantStatus;
import com.saasbase.tenant.domain.gateway.TenantGateway;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class RedisTenantAuthStateGatewayTest {
    private static final String KEY = "saasbase:tenant:auth-state:7";

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private TenantGateway tenantGateway;

    private RedisTenantAuthStateGateway gateway;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        gateway = new RedisTenantAuthStateGateway(redisTemplate, tenantGateway, objectMapper);
    }

    @Test
    void cacheUsesStableJsonKeyAndExactFiveSecondTtl() {
        gateway.cache(new TenantAuthState(7L, TenantStatus.DISABLED, 3));

        verify(valueOperations).set(KEY, "{\"status\":\"DISABLED\",\"sessionVersion\":3}", Duration.ofSeconds(5));
    }

    @Test
    void validCacheHitDoesNotQueryDatabase() {
        when(valueOperations.get(KEY)).thenReturn("{\"status\":\"ACTIVE\",\"sessionVersion\":2}");

        assertThat(gateway.requireCurrent(7L)).isEqualTo(new TenantAuthState(7L, TenantStatus.ACTIVE, 2));
        verify(tenantGateway, never()).findById(7L);
    }

    @Test
    void cacheMissLoadsDatabaseAndBackfillsCache() {
        Tenant tenant = tenant(TenantStatus.DISABLED, 4);
        when(valueOperations.get(KEY)).thenReturn(null);
        when(tenantGateway.findById(7L)).thenReturn(Optional.of(tenant));

        assertThat(gateway.requireCurrent(7L)).isEqualTo(tenant.authState());
        verify(valueOperations).set(KEY, "{\"status\":\"DISABLED\",\"sessionVersion\":4}", Duration.ofSeconds(5));
    }

    @Test
    void redisReadFailureLoadsDatabase() {
        Tenant tenant = tenant(TenantStatus.ACTIVE, 1);
        when(valueOperations.get(KEY)).thenThrow(new RedisConnectionFailureException("down"));
        when(tenantGateway.findById(7L)).thenReturn(Optional.of(tenant));

        assertThat(gateway.requireCurrent(7L)).isEqualTo(tenant.authState());
    }

    @ParameterizedTest
    @ValueSource(strings = {"broken", "{\"status\":\"SUSPENDED\",\"sessionVersion\":1}",
            "{\"status\":\"ACTIVE\",\"sessionVersion\":-1}"})
    void invalidCachedValueLoadsDatabaseAndOverwritesIt(String cachedValue) {
        Tenant tenant = tenant(TenantStatus.ACTIVE, 5);
        when(valueOperations.get(KEY)).thenReturn(cachedValue);
        when(tenantGateway.findById(7L)).thenReturn(Optional.of(tenant));

        assertThat(gateway.requireCurrent(7L)).isEqualTo(tenant.authState());
        verify(valueOperations).set(KEY, "{\"status\":\"ACTIVE\",\"sessionVersion\":5}", Duration.ofSeconds(5));
    }

    @Test
    void missingDatabaseTenantThrowsBusinessError() {
        when(tenantGateway.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gateway.requireCurrent(7L))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(ErrorCode.TENANT_NOT_FOUND);
    }

    @Test
    void databaseFailurePropagatesUnchanged() {
        IllegalStateException failure = new IllegalStateException("db failed");
        when(tenantGateway.findById(7L)).thenThrow(failure);

        assertThatThrownBy(() -> gateway.requireCurrent(7L)).isSameAs(failure);
    }

    @Test
    void backfillFailureDoesNotHideAuthoritativeDatabaseValue() {
        Tenant tenant = tenant(TenantStatus.ACTIVE, 8);
        when(tenantGateway.findById(7L)).thenReturn(Optional.of(tenant));
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
                .when(valueOperations).set(KEY, "{\"status\":\"ACTIVE\",\"sessionVersion\":8}", Duration.ofSeconds(5));

        assertThat(gateway.requireCurrent(7L)).isEqualTo(tenant.authState());
    }

    @Test
    void directCacheWriteFailurePropagates() {
        RedisConnectionFailureException failure = new RedisConnectionFailureException("down");
        org.mockito.Mockito.doThrow(failure).when(valueOperations)
                .set(KEY, "{\"status\":\"ACTIVE\",\"sessionVersion\":1}", Duration.ofSeconds(5));

        assertThatThrownBy(() -> gateway.cache(new TenantAuthState(7L, TenantStatus.ACTIVE, 1))).isSameAs(failure);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void requireCurrentRejectsInvalidTenantId(Long tenantId) {
        assertThatThrownBy(() -> gateway.requireCurrent(tenantId)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cacheRejectsNullStateAndNonPositiveTenantId() {
        assertThatThrownBy(() -> gateway.cache(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.cache(new TenantAuthState(0L, TenantStatus.ACTIVE, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Tenant tenant(TenantStatus status, long sessionVersion) {
        return Tenant.reconstitute(7L, "acme", "Acme", status, sessionVersion, 0);
    }
}
