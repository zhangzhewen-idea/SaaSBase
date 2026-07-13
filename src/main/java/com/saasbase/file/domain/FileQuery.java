package com.saasbase.file.domain;

import java.time.Instant;

public record FileQuery(
        String filename,
        String contentType,
        Instant uploadedFrom,
        Instant uploadedTo,
        long pageNo,
        long pageSize
) {
    public FileQuery {
        if (pageNo < 1) {
            throw new IllegalArgumentException("pageNo must be at least 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize must be between 1 and 100");
        }
        if (uploadedFrom != null && uploadedTo != null && uploadedFrom.isAfter(uploadedTo)) {
            throw new IllegalArgumentException("uploadedFrom must not be after uploadedTo");
        }
    }
}
