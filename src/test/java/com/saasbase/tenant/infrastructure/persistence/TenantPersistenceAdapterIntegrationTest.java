package com.saasbase.tenant.infrastructure.persistence;

import com.saasbase.tenant.domain.Tenant;
import com.saasbase.tenant.domain.TenantStatus;
import com.saasbase.tenant.domain.gateway.TenantGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class TenantPersistenceAdapterIntegrationTest {
    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired TenantGateway gateway;
    @Autowired JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM tenant");
    }

    @Test
    void insertsTenantAndReturnsPersistedAggregate() {
        Tenant saved = gateway.insert(Tenant.create("acme", "Acme"), 7L);

        assertThat(saved.id()).isNotNull().isNotNegative();
        assertThat(saved.tenantCode()).isEqualTo("acme");
        assertThat(saved.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(saved.sessionVersion()).isZero();
        assertThat(saved.version()).isZero();
        var row = jdbcTemplate.queryForMap("SELECT * FROM tenant WHERE id = ?", saved.id());
        assertThat(row).containsEntry("tenant_code", "acme").containsEntry("tenant_name", "Acme")
                .containsEntry("status", "ACTIVE").containsEntry("created_by", 7L)
                .containsEntry("updated_by", 7L).containsEntry("deleted", false)
                .containsEntry("version", 0L).containsEntry("session_version", 0L);
        assertThat(row.get("created_at")).isEqualTo(row.get("updated_at"));
    }

    @Test
    void findsAndChecksOnlyNonDeletedTenant() {
        insert(1, "visible", "Visible", "DISABLED", 3, 4, false, Instant.parse("2026-01-01T00:00:00Z"));
        insert(2, "deleted", "Deleted", "ACTIVE", 0, 0, true, Instant.parse("2026-01-02T00:00:00Z"));

        assertThat(gateway.existsByCode("visible")).isTrue();
        assertThat(gateway.findById(1L)).get().satisfies(tenant -> {
            assertThat(tenant.tenantCode()).isEqualTo("visible");
            assertThat(tenant.tenantName()).isEqualTo("Visible");
            assertThat(tenant.status()).isEqualTo(TenantStatus.DISABLED);
            assertThat(tenant.sessionVersion()).isEqualTo(3);
            assertThat(tenant.version()).isEqualTo(4);
        });
        assertThat(gateway.existsByCode("deleted")).isFalse();
        assertThat(gateway.findById(2L)).isEmpty();
    }

    @Test
    void rejectsDuplicateTenantCode() {
        gateway.insert(Tenant.create("duplicate", "First"), 1L);
        assertThatThrownBy(() -> gateway.insert(Tenant.create("duplicate", "Second"), 1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pagesWithExactCodeLiteralNameStatusAndStableOrdering() {
        insert(1, "alpha", "100% Real", "ACTIVE", 0, 0, false, Instant.parse("2026-01-01T00:00:00Z"));
        insert(2, "beta", "100 Percent", "DISABLED", 0, 0, false, Instant.parse("2026-01-03T00:00:00Z"));
        insert(3, "gamma", "Under_score", "ACTIVE", 0, 0, false, Instant.parse("2026-01-03T00:00:00Z"));
        insert(4, "deleted", "100% Real", "ACTIVE", 0, 0, true, Instant.parse("2026-01-04T00:00:00Z"));
        insert(5, "slash", "Back\\slash", "DISABLED", 0, 0, false, Instant.parse("2026-01-02T00:00:00Z"));

        assertThat(gateway.page(new TenantGateway.Query(" alpha ", null, null, 1, 10)).items())
                .extracting(Tenant::id).containsExactly(1L);
        assertThat(gateway.page(new TenantGateway.Query(null, "%", null, 1, 10)).items())
                .extracting(Tenant::id).containsExactly(1L);
        assertThat(gateway.page(new TenantGateway.Query(null, "_", null, 1, 10)).items())
                .extracting(Tenant::id).containsExactly(3L);
        assertThat(gateway.page(new TenantGateway.Query(null, "\\", null, 1, 10)).items())
                .extracting(Tenant::id).containsExactly(5L);
        var active = gateway.page(new TenantGateway.Query(null, null, TenantStatus.ACTIVE, 1, 1));
        assertThat(active.total()).isEqualTo(2);
        assertThat(active.items()).extracting(Tenant::id).containsExactly(3L);
        assertThat(gateway.page(new TenantGateway.Query(null, null, TenantStatus.ACTIVE, 2, 1)).items())
                .extracting(Tenant::id).containsExactly(1L);
    }

    @Test
    void updatesAtomicallyUsingVersion() {
        insert(1, "acme", "Before", "ACTIVE", 2, 5, false, Instant.parse("2026-01-01T00:00:00Z"));
        Tenant current = gateway.findById(1L).orElseThrow();
        current.rename("After");
        current.disable();

        assertThat(gateway.update(current, 9L)).isTrue();
        assertThat(gateway.findById(1L)).get().satisfies(saved -> {
            assertThat(saved.tenantName()).isEqualTo("After");
            assertThat(saved.status()).isEqualTo(TenantStatus.DISABLED);
            assertThat(saved.sessionVersion()).isEqualTo(3);
            assertThat(saved.version()).isEqualTo(6);
        });
        assertThat(jdbcTemplate.queryForObject("SELECT updated_by FROM tenant WHERE id = 1", Long.class)).isEqualTo(9L);

        Tenant stale = Tenant.reconstitute(1L, "acme", "Stale", TenantStatus.ACTIVE, 99, 5);
        assertThat(gateway.update(stale, 10L)).isFalse();
        assertThat(gateway.findById(1L)).get().extracting(Tenant::tenantName).isEqualTo("After");
    }

    private void insert(long id, String code, String name, String status, long sessionVersion, long version,
                        boolean deleted, Instant createdAt) {
        Timestamp time = Timestamp.from(createdAt);
        jdbcTemplate.update("""
                INSERT INTO tenant (id, tenant_code, tenant_name, status, session_version, created_at, updated_at, deleted, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, code, name, status, sessionVersion, time, time, deleted, version);
    }
}
