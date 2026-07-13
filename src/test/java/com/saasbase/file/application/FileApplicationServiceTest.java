package com.saasbase.file.application;

import com.saasbase.file.domain.FileMetadata;
import com.saasbase.file.domain.FileQuery;
import com.saasbase.file.domain.FileStatus;
import com.saasbase.file.domain.StoredObject;
import com.saasbase.file.domain.gateway.FileStorageGateway;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

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

    @Test
    void rejects_invalid_available_metadata_and_negative_numeric_fields() {
        Instant createdAt = Instant.parse("2026-07-13T08:00:00Z");

        assertThatIllegalArgumentException().isThrownBy(() -> new FileMetadata(
                7L, 2001L, null, null, "report.txt", "text/plain", "txt", 5L,
                FileStatus.AVAILABLE, createdAt, 3001L, 0L));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileMetadata(
                7L, 2001L, "local", "2001/object", "report.txt", "text/plain", "txt", -1L,
                FileStatus.AVAILABLE, createdAt, 3001L, 0L));
        assertThatIllegalArgumentException().isThrownBy(() -> FileMetadata.uploading(
                7L, 2001L, "report.txt", "text/plain", "txt", createdAt, 3001L, -1L));
    }

    @Test
    void rejects_invalid_metadata_state_transitions() {
        Instant createdAt = Instant.parse("2026-07-13T08:00:00Z");
        FileMetadata uploading = FileMetadata.uploading(
                7L, 2001L, "report.txt", "text/plain", "txt", createdAt, 3001L, 0L);
        FileMetadata available = uploading.markAvailable("local", "2001/object", 5L);

        assertThatIllegalStateException().isThrownBy(() -> available.markAvailable("local", "other", 6L));
        assertThatIllegalStateException().isThrownBy(uploading::markDeleteFailed);
        assertThatIllegalStateException().isThrownBy(available.markDeleteFailed()::markDeleteFailed);
    }

    @Test
    void file_query_accepts_boundaries_and_rejects_invalid_pages_or_time_range() {
        Instant from = Instant.parse("2026-07-13T08:00:00Z");
        Instant to = Instant.parse("2026-07-13T09:00:00Z");

        assertThat(new FileQuery(null, null, from, from, 1, 1).pageSize()).isEqualTo(1);
        assertThat(new FileQuery(null, null, null, to, 1, 100).pageSize()).isEqualTo(100);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FileQuery(null, null, null, null, 0, 10));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FileQuery(null, null, null, null, 1, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FileQuery(null, null, null, null, 1, 101));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FileQuery(null, null, to, from, 1, 10));
    }

    @Test
    void stored_object_rejects_blank_identity_and_negative_size() {
        assertThatIllegalArgumentException().isThrownBy(() -> new StoredObject(" ", "key", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new StoredObject("local", " ", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new StoredObject("local", "key", -1));
    }
}
