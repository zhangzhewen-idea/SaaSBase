package com.saasbase.file.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
}
