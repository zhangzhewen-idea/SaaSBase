package com.saasbase.file.adapter;

import com.saasbase.file.domain.FileQuery;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

public record FileListQuery(
        String filename,
        String contentType,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant uploadedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant uploadedTo,
        Long pageNo,
        Long pageSize) {

    public FileQuery toFileQuery() {
        return new FileQuery(
                filename,
                contentType,
                uploadedFrom,
                uploadedTo,
                pageNo == null || pageNo < 1 ? 1 : pageNo,
                pageSize == null || pageSize < 1 ? 10 : pageSize);
    }
}
