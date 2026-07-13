package com.saasbase.file.application;

import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.common.tenant.TenantContext;
import com.saasbase.common.tenant.TenantContextHolder;
import com.saasbase.file.domain.FileMetadata;
import com.saasbase.file.domain.FileStatus;
import com.saasbase.file.domain.gateway.FileMetadataGateway;
import com.saasbase.file.domain.gateway.FileStorageGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileApplicationServiceDeleteTest {

    @Mock
    FileMetadataGateway metadataGateway;

    @Mock
    FileStorageGateway storageGateway;

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void deletes_file_and_logically_removes_metadata() {
        TenantContextHolder.set(new TenantContext(2001L, 3001L, false));
        FileApplicationService service = new FileApplicationService(policy(), metadataGateway, storageGateway,
                Clock.fixed(Instant.parse("2026-07-13T08:00:00Z"), ZoneOffset.UTC));
        FileMetadata metadata = new FileMetadata(
                7001L, 2001L, "local", "2001/object", "report.pdf", "application/pdf", "pdf", 5L,
                FileStatus.AVAILABLE, Instant.parse("2026-07-13T08:00:00Z"), 3001L, 1L);
        when(metadataGateway.findDeletableById(7001L)).thenReturn(Optional.of(metadata));

        service.delete(7001L);

        verify(storageGateway).delete(2001L, "2001/object");
        verify(metadataGateway).logicallyDelete(7001L, 3001L, 1L);
    }

    @Test
    void returns_file_not_found_when_metadata_missing() {
        TenantContextHolder.set(new TenantContext(2001L, 3001L, false));
        FileApplicationService service = new FileApplicationService(policy(), metadataGateway, storageGateway,
                Clock.fixed(Instant.parse("2026-07-13T08:00:00Z"), ZoneOffset.UTC));
        when(metadataGateway.findDeletableById(7001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(7001L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.FILE_NOT_FOUND);
    }

    private FilePolicy policy() {
        return new FilePolicy(
                20 * 1024 * 1024L,
                Set.of("pdf"),
                Set.of("application/pdf"),
                Set.of("application/pdf"),
                Map.of("pdf", Set.of("application/pdf")));
    }
}
