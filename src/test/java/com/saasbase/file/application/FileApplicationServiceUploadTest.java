package com.saasbase.file.application;

import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.common.tenant.TenantContext;
import com.saasbase.common.tenant.TenantContextHolder;
import com.saasbase.file.domain.FileMetadata;
import com.saasbase.file.domain.FileStatus;
import com.saasbase.file.domain.StoredObject;
import com.saasbase.file.domain.gateway.FileMetadataGateway;
import com.saasbase.file.domain.gateway.FileStorageGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileApplicationServiceUploadTest {

    @Mock
    FileMetadataGateway metadataGateway;

    @Mock
    FileStorageGateway storageGateway;

    @Captor
    ArgumentCaptor<FileMetadata> metadataCaptor;

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void uploads_and_marks_file_available() {
        TenantContextHolder.set(new TenantContext(2001L, 3001L, false));
        FileApplicationService service = new FileApplicationService(policy(), metadataGateway, storageGateway,
                Clock.fixed(Instant.parse("2026-07-13T08:00:00Z"), ZoneOffset.UTC));
        when(storageGateway.store(eq(2001L), any(InputStream.class)))
                .thenReturn(new StoredObject("local", "2001/object", 5L));
        doAnswer(invocation -> {
            FileMetadata metadata = invocation.getArgument(0);
            return new FileMetadata(
                    7001L,
                    metadata.tenantId(),
                    metadata.storageType(),
                    metadata.objectKey(),
                    metadata.originalFilename(),
                    metadata.contentType(),
                    metadata.extension(),
                    metadata.size(),
                    metadata.status(),
                    metadata.createdAt(),
                    metadata.createdBy(),
                    metadata.version());
        }).when(metadataGateway).createUploading(any(FileMetadata.class));

        FileMetadata result = service.upload("report.pdf", "application/pdf", 5L,
                new ByteArrayInputStream("hello".getBytes()));

        verify(metadataGateway).createUploading(metadataCaptor.capture());
        assertThat(metadataCaptor.getValue().id()).isNull();
        verify(metadataGateway).markAvailable(7001L, "local", "2001/object", 5L, 0L);
        assertThat(result.status()).isEqualTo(FileStatus.AVAILABLE);
        assertThat(result.objectKey()).isEqualTo("2001/object");
        assertThat(result.id()).isEqualTo(7001L);
    }

    @Test
    void removes_uploading_metadata_when_store_fails() {
        TenantContextHolder.set(new TenantContext(2001L, 3001L, false));
        FileApplicationService service = new FileApplicationService(policy(), metadataGateway, storageGateway,
                Clock.fixed(Instant.parse("2026-07-13T08:00:00Z"), ZoneOffset.UTC));
        doAnswer(invocation -> {
            FileMetadata metadata = invocation.getArgument(0);
            return new FileMetadata(
                    7001L,
                    metadata.tenantId(),
                    metadata.storageType(),
                    metadata.objectKey(),
                    metadata.originalFilename(),
                    metadata.contentType(),
                    metadata.extension(),
                    metadata.size(),
                    metadata.status(),
                    metadata.createdAt(),
                    metadata.createdBy(),
                    metadata.version());
        }).when(metadataGateway).createUploading(any(FileMetadata.class));
        doThrow(new IllegalStateException("boom")).when(storageGateway).store(eq(2001L), any(InputStream.class));

        assertThatThrownBy(() -> service.upload("report.pdf", "application/pdf", 5L,
                new ByteArrayInputStream("hello".getBytes())))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.FILE_STORAGE_FAILED);

        verify(metadataGateway).removeUploading(any(Long.class));
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
