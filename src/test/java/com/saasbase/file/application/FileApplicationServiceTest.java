package com.saasbase.file.application;

import com.saasbase.file.domain.FileMetadata;
import com.saasbase.file.domain.FileStatus;
import com.saasbase.file.domain.StoredObject;
import com.saasbase.file.domain.gateway.FileStorageGateway;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FileApplicationServiceTest {

    @Test
    void uploading_metadata_can_become_available_without_losing_metadata() {
        Instant createdAt = Instant.parse("2026-07-13T08:00:00Z");
        FileMetadata uploading = FileMetadata.uploading(
                7L, 2001L, "report.txt", "text/plain", "txt", createdAt, 3001L, 4L);

        FileMetadata available = uploading.markAvailable("local", "2001/object", 5L);

        assertThat(available).isEqualTo(new FileMetadata(
                7L, 2001L, "local", "2001/object", "report.txt", "text/plain", "txt", 5L,
                FileStatus.AVAILABLE, createdAt, 3001L, 4L));
        assertThat(uploading.status()).isEqualTo(FileStatus.UPLOADING);
    }

    @Test
    void metadata_can_mark_delete_failure_without_losing_other_fields() {
        FileMetadata available = new FileMetadata(
                7L, 2001L, "local", "2001/object", "report.txt", "text/plain", "txt", 5L,
                FileStatus.AVAILABLE, Instant.parse("2026-07-13T08:00:00Z"), 3001L, 4L);

        FileMetadata failed = available.markDeleteFailed();

        assertThat(failed.status()).isEqualTo(FileStatus.DELETE_FAILED);
        assertThat(failed).usingRecursiveComparison()
                .ignoringFields("status")
                .isEqualTo(available);
    }

    @Test
    void storage_gateway_exposes_the_new_storage_contract() throws Exception {
        var store = FileStorageGateway.class.getMethod("store", Long.class, InputStream.class);
        var delete = FileStorageGateway.class.getMethod("delete", Long.class, String.class);

        assertThat(store.getReturnType()).isEqualTo(StoredObject.class);
        assertThat(delete.getReturnType()).isEqualTo(void.class);
    }

    @Test
    void metadata_gateway_exposes_command_and_optional_query_contracts() throws Exception {
        var gateway = com.saasbase.file.domain.gateway.FileMetadataGateway.class;

        assertThat(gateway.getMethod("markAvailable", Long.class, String.class, String.class,
                long.class, long.class).getReturnType()).isEqualTo(void.class);
        assertThat(gateway.getMethod("markDeleteFailed", Long.class, long.class).getReturnType())
                .isEqualTo(void.class);
        assertThat(gateway.getMethod("logicallyDelete", Long.class, Long.class, long.class).getReturnType())
                .isEqualTo(void.class);
        assertThat(gateway.getMethod("findAvailableById", Long.class).getGenericReturnType().getTypeName())
                .isEqualTo(Optional.class.getName() + "<com.saasbase.file.domain.FileMetadata>");
        assertThat(gateway.getMethod("findDeletableById", Long.class).getGenericReturnType().getTypeName())
                .isEqualTo(Optional.class.getName() + "<com.saasbase.file.domain.FileMetadata>");
    }
}
