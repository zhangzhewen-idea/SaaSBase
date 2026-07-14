package com.saasbase.file.infrastructure.persistence;

import com.saasbase.common.api.PageResponse;
import com.saasbase.file.domain.FileMetadata;
import com.saasbase.file.domain.FileQuery;
import com.saasbase.file.domain.gateway.FileMetadataGateway;
import com.saasbase.common.tenant.TenantContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class FileMetadataPersistenceAdapter implements FileMetadataGateway {

    private final FileMetadataMapper mapper;

    public FileMetadataPersistenceAdapter(FileMetadataMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public FileMetadata createUploading(FileMetadata metadata) {
        FileMetadataRecord record = toRecord(metadata);
        mapper.insertUploading(record);
        return toDomain(record);
    }

    @Override
    public void markAvailable(Long id, String storageType, String objectKey, long size, long version) {
        updateOne(id, mapper.markAvailable(tenantId(), id, storageType, objectKey, size, version, Instant.now(), null));
    }

    @Override
    public void markDeleteFailed(Long id, long version) {
        updateOne(id, mapper.markDeleteFailed(tenantId(), id, version, Instant.now(), null));
    }

    @Override
    public Optional<FileMetadata> findAvailableById(Long id) {
        return mapper.findAvailableById(tenantId(), id).map(this::toDomain);
    }

    @Override
    public Optional<FileMetadata> findDeletableById(Long id) {
        return mapper.findDeletableById(tenantId(), id).map(this::toDomain);
    }

    @Override
    public PageResponse<FileMetadata> search(FileQuery query) {
        long total = mapper.countSearch(tenantId(), query.filename(), query.contentType(), query.uploadedFrom(), query.uploadedTo());
        long offset = (query.pageNo() - 1) * query.pageSize();
        List<FileMetadata> items = mapper.search(tenantId(), query.filename(), query.contentType(), query.uploadedFrom(),
                        query.uploadedTo(), offset, query.pageSize())
                .stream()
                .map(this::toDomain)
                .toList();
        return new PageResponse<>(items, total, query.pageNo(), query.pageSize());
    }

    @Override
    public void logicallyDelete(Long id, Long deletedBy, long version) {
        updateOne(id, mapper.logicallyDelete(tenantId(), id, Instant.now(), deletedBy, version));
    }

    @Override
    public void removeUploading(Long id) {
        mapper.removeUploading(tenantId(), id);
    }

    private void updateOne(Long id, int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException("file metadata update failed for id=" + id);
        }
    }

    private Long tenantId() {
        return TenantContextHolder.require().tenantId();
    }

    private FileMetadataRecord toRecord(FileMetadata metadata) {
        String storageType = metadata.status() == com.saasbase.file.domain.FileStatus.UPLOADING ? "" : metadata.storageType();
        String objectKey = metadata.status() == com.saasbase.file.domain.FileStatus.UPLOADING ? "" : metadata.objectKey();
        return new FileMetadataRecord(
                metadata.id(),
                metadata.tenantId(),
                storageType,
                objectKey,
                metadata.originalFilename(),
                metadata.contentType(),
                metadata.extension(),
                metadata.size(),
                metadata.status(),
                metadata.createdAt(),
                metadata.createdBy(),
                metadata.createdAt(),
                metadata.createdBy(),
                false,
                null,
                null,
                metadata.version());
    }

    private FileMetadata toDomain(FileMetadataRecord record) {
        String storageType = record.getStorageType();
        String objectKey = record.getObjectKey();
        if (record.getStatus() == com.saasbase.file.domain.FileStatus.UPLOADING) {
            storageType = null;
            objectKey = null;
        }
        return new FileMetadata(
                record.getId(),
                record.getTenantId(),
                storageType,
                objectKey,
                record.getOriginalFilename(),
                record.getContentType(),
                record.getExtension(),
                record.getSize() == null ? 0L : record.getSize(),
                record.getStatus(),
                record.getCreatedAt(),
                record.getCreatedBy(),
                record.getVersion() == null ? 0L : record.getVersion());
    }
}
