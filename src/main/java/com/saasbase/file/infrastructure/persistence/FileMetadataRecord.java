package com.saasbase.file.infrastructure.persistence;

import com.saasbase.file.domain.FileStatus;

import java.time.Instant;

public record FileMetadataRecord(
        Long id,
        Long tenantId,
        String storageType,
        String objectKey,
        String originalFilename,
        String contentType,
        String extension,
        Long size,
        FileStatus status,
        Instant createdAt,
        Long createdBy,
        Instant updatedAt,
        Long updatedBy,
        Boolean deleted,
        Instant deletedAt,
        Long deletedBy,
        Long version) {
}
