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
    public FileMetadata {
        requireNonNull(id, "id");
        requireNonNull(tenantId, "tenantId");
        requireNonBlank(originalFilename, "originalFilename");
        requireNonBlank(contentType, "contentType");
        requireNonBlank(extension, "extension");
        requireNonNull(status, "status");
        requireNonNull(createdAt, "createdAt");
        requireNonNull(createdBy, "createdBy");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        if (status == FileStatus.UPLOADING) {
            if (storageType != null || objectKey != null || size != 0) {
                throw new IllegalArgumentException("uploading metadata must not have a stored object");
            }
        } else {
            requireNonBlank(storageType, "storageType");
            requireNonBlank(objectKey, "objectKey");
        }
    }

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
        if (status != FileStatus.UPLOADING) {
            throw new IllegalStateException("only uploading metadata can become available");
        }
        return new FileMetadata(
                id, tenantId, storageType, objectKey, originalFilename, contentType, extension, size,
                FileStatus.AVAILABLE, createdAt, createdBy, version);
    }

    public FileMetadata markDeleteFailed() {
        if (status != FileStatus.AVAILABLE) {
            throw new IllegalStateException("only available metadata can mark delete failure");
        }
        return new FileMetadata(
                id, tenantId, storageType, objectKey, originalFilename, contentType, extension, size,
                FileStatus.DELETE_FAILED, createdAt, createdBy, version);
    }

    private static void requireNonNull(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
