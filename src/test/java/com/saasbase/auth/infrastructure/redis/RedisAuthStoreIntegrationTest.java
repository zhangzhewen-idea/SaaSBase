package com.saasbase.auth.infrastructure.redis;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RedisAuthStoreIntegrationTest {
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        REDIS.stop();
    }

    @Test
    void stores_and_revokes_refresh_token_in_real_redis() {
        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate);

        store.save("refresh-integration", "1001|2001|alice|tenant:user:read",
                java.time.Instant.now().plusSeconds(3600).getEpochSecond());

        assertThat(store.find("refresh-integration")).isEqualTo("1001|2001|alice|tenant:user:read");
        assertThat(store.exists("refresh-integration")).isTrue();

        store.revoke("refresh-integration");

        assertThat(store.exists("refresh-integration")).isFalse();
    }

    @Test
    void stores_and_reads_access_token_revocation_in_real_redis() {
        RedisTokenRevocationStore store = new RedisTokenRevocationStore(redisTemplate);

        assertThat(store.isRevoked("jti-integration")).isFalse();

        store.revoke("jti-integration", java.time.Instant.now().plusSeconds(3600).getEpochSecond());

        assertThat(store.isRevoked("jti-integration")).isTrue();
    }
}
