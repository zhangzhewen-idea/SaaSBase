package com.saasbase.file.domain.gateway;

import com.saasbase.common.api.PageResponse;
import com.saasbase.file.domain.FileMetadata;
import com.saasbase.file.domain.FileQuery;

import java.util.Optional;

public interface FileMetadataGateway {
    FileMetadata createUploading(FileMetadata metadata);

    void markAvailable(Long id, String storageType, String objectKey, long size, long version);

    void markDeleteFailed(Long id, long version);

    Optional<FileMetadata> findAvailableById(Long id);

    Optional<FileMetadata> findDeletableById(Long id);

    PageResponse<FileMetadata> search(FileQuery query);

    void logicallyDelete(Long id, Long deletedBy, long version);

    void removeUploading(Long id);
}
