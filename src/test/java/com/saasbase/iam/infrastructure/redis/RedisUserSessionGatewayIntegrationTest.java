package com.saasbase.iam.infrastructure.redis;

import com.saasbase.iam.domain.UserAuthState;
import com.saasbase.iam.domain.UserStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RedisUserSessionGatewayIntegrationTest {
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisUserSessionGateway gateway;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        gateway = new RedisUserSessionGateway(redisTemplate);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        REDIS.stop();
    }

    @Test
    void storesJsonWithFiveSecondTtl() {
        gateway.put(new UserAuthState(1L, 2L, UserStatus.ACTIVE, 3L, true));

        String key = "user:auth-state:1:2";
        assertThat(redisTemplate.opsForValue().get(key)).contains("\"tenantId\":1");
        assertThat(redisTemplate.getExpire(key)).isBetween(1L, 5L);
    }

    @Test
    void getReturnsCachedValueAndFallsBackWhenMissingOrBroken() {
        UserAuthState cached = new UserAuthState(1L, 2L, UserStatus.DISABLED, 8L, false);
        gateway.put(cached);

        assertThat(gateway.get(1L, 2L)).hasValue(cached);
        assertThat(gateway.get(9L, 9L)).isEmpty();

        AtomicInteger loaderCalls = new AtomicInteger();
        UserAuthState loaded = gateway.getOrLoad(9L, 9L, () -> {
            loaderCalls.incrementAndGet();
            return new UserAuthState(9L, 9L, UserStatus.ACTIVE, 1L, false);
        });

        assertThat(loaderCalls.get()).isEqualTo(1);
        assertThat(loaded.tenantId()).isEqualTo(9L);
    }

    @Test
    void survivesRedisWriteFailureWithoutThrowing() {
        RedisUserSessionGateway brokenGateway = new RedisUserSessionGateway(new StringRedisTemplate()) {
            @Override
            public void put(UserAuthState state) {
                throw new RuntimeException("redis down");
            }
        };

        assertThat(brokenGateway.getOrLoad(1L, 1L, () -> new UserAuthState(1L, 1L, UserStatus.ACTIVE, 1L, true)).userId())
                .isEqualTo(1L);
    }
}
