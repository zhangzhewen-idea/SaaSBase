package com.saasbase.file.domain;

import java.time.Instant;

public record FileMetadata(
        Long id,
        Long tenantId,
        String storageType,
        String objectKey,
        String originalFilename,
        String contentType,
        String extension,
        long size,
        FileStatus status,
        Instant createdAt,
        Long createdBy,
        long version
) {
    public static FileMetadata uploading(
            Long id,
            Long tenantId,
            String originalFilename,
            String contentType,
            String extension,
            Instant createdAt,
            Long createdBy,
            long version
    ) {
        return new FileMetadata(
                id, tenantId, null, null, originalFilename, contentType, extension, 0,
                FileStatus.UPLOADING, createdAt, createdBy, version);
    }

    public FileMetadata markAvailable(String storageType, String objectKey, long size) {
        return new FileMetadata(
                id, tenantId, storageType, objectKey, originalFilename, contentType, extension, size,
                FileStatus.AVAILABLE, createdAt, createdBy, version);
    }

    public FileMetadata markDeleteFailed() {
        return new FileMetadata(
                id, tenantId, storageType, objectKey, originalFilename, contentType, extension, size,
                FileStatus.DELETE_FAILED, createdAt, createdBy, version);
    }
}
