package com.saasbase.file.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageGatewayTest {

    @TempDir
    Path tempDir;

    @Test
    void stores_file_with_server_generated_object_key() throws Exception {
        LocalFileStorageGateway gateway = new LocalFileStorageGateway(new FileStorageProperties(
                tempDir,
                DataSize.ofMegabytes(20),
                Set.of("pdf"),
                Set.of("application/pdf"),
                Set.of("application/pdf"),
                Map.of("pdf", Set.of("application/pdf"))));

        var stored = gateway.store(2001L, new ByteArrayInputStream("hello".getBytes()));

        assertThat(stored.objectKey()).isNotEqualTo("report.txt");
        assertThat(stored.objectKey()).doesNotContain("..");
        assertThat(tempDir.resolve(stored.objectKey())).exists();
    }

    @Test
    void loads_file_by_tenant_scoped_object_key() throws IOException {
        LocalFileStorageGateway gateway = new LocalFileStorageGateway(new FileStorageProperties(
                tempDir,
                DataSize.ofMegabytes(20),
                Set.of("pdf"),
                Set.of("application/pdf"),
                Set.of("application/pdf"),
                Map.of("pdf", Set.of("application/pdf"))));

        var stored = gateway.store(2001L, new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        try (var inputStream = gateway.load(2001L, stored.objectKey())) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
        }
    }

    @Test
    void deletes_file_and_accepts_missing_file_as_success() {
        LocalFileStorageGateway gateway = new LocalFileStorageGateway(new FileStorageProperties(
                tempDir,
                DataSize.ofMegabytes(20),
                Set.of("pdf"),
                Set.of("application/pdf"),
                Set.of("application/pdf"),
                Map.of("pdf", Set.of("application/pdf"))));

        var stored = gateway.store(2001L, new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        gateway.delete(2001L, stored.objectKey());

        assertThat(tempDir.resolve(stored.objectKey())).doesNotExist();

        gateway.delete(2001L, stored.objectKey());
    }

    @Test
    void rejects_cross_tenant_object_key() {
        LocalFileStorageGateway gateway = new LocalFileStorageGateway(new FileStorageProperties(
                tempDir,
                DataSize.ofMegabytes(20),
                Set.of("pdf"),
                Set.of("application/pdf"),
                Set.of("application/pdf"),
                Map.of("pdf", Set.of("application/pdf"))));

        assertThatThrownBy(() -> gateway.load(2001L, "2002/anything"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant");
    }
}
