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
}
