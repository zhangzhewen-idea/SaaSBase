package com.saasbase.file.infrastructure.persistence;

import com.saasbase.common.api.PageResponse;
import com.saasbase.common.tenant.TenantContext;
import com.saasbase.common.tenant.TenantContextHolder;
import com.saasbase.file.domain.FileMetadata;
import com.saasbase.file.domain.FileQuery;
import com.saasbase.file.domain.FileStatus;
import com.saasbase.file.domain.gateway.FileMetadataGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class FileMetadataPersistenceAdapterIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    FileMetadataGateway gateway;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> mysql.getJdbcUrl()
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @BeforeEach
    void cleanDatabase() {
        TenantContextHolder.set(new TenantContext(2001L, 3001L, false));
        jdbcTemplate.update("DELETE FROM file_metadata");
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void creates_reads_updates_and_deletes_metadata_with_tenant_boundary() {
        Instant createdAt = Instant.parse("2026-07-13T08:00:00Z");
        FileMetadata uploading = FileMetadata.uploading(
                7001L, 2001L, "report.pdf", "application/pdf", "pdf", createdAt, 3001L, 0L);

        gateway.createUploading(uploading);

        assertThat(gateway.findAvailableById(7001L)).isEmpty();
        assertThat(gateway.findDeletableById(7001L)).isEmpty();

        gateway.markAvailable(7001L, "local", "2001/uuid-1", 15L, 0L);

        FileMetadata available = gateway.findAvailableById(7001L).orElseThrow();
        assertThat(available.status()).isEqualTo(FileStatus.AVAILABLE);
        assertThat(available.objectKey()).isEqualTo("2001/uuid-1");
        assertThat(available.size()).isEqualTo(15L);

        PageResponse<FileMetadata> page = gateway.search(new FileQuery("report", "application/pdf", null, null, 1, 10));
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items()).singleElement().extracting(FileMetadata::id).isEqualTo(7001L);

        gateway.logicallyDelete(7001L, 3001L, 1L);

        assertThat(gateway.findAvailableById(7001L)).isEmpty();
        assertThat(gateway.findDeletableById(7001L)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM file_metadata WHERE id = ?",
                Boolean.class,
                7001L)).isTrue();
    }
}
