package com.saasbase.file.adapter;

import com.saasbase.file.domain.FileMetadata;
import com.saasbase.file.domain.FileStatus;

import java.time.Instant;

public record FileView(
        Long id,
        Long tenantId,
        String originalFilename,
        String contentType,
        String extension,
        long size,
        FileStatus status,
        Instant createdAt,
        Long createdBy) {

    public static FileView from(FileMetadata metadata) {
        return new FileView(
                metadata.id(),
                metadata.tenantId(),
                metadata.originalFilename(),
                metadata.contentType(),
                metadata.extension(),
                metadata.size(),
                metadata.status(),
                metadata.createdAt(),
                metadata.createdBy());
    }
}
