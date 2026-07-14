package com.saasbase.file.application;

import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.common.api.PageResponse;
import com.saasbase.common.tenant.TenantContextHolder;
import com.saasbase.file.domain.FileMetadata;
import com.saasbase.file.domain.FileQuery;
import com.saasbase.file.domain.gateway.FileMetadataGateway;
import com.saasbase.file.domain.gateway.FileStorageGateway;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;

@Service
public class FileApplicationService {

    private final FilePolicy filePolicy;
    private final FileMetadataGateway metadataGateway;
    private final FileStorageGateway storageGateway;
    private final Clock clock;

    @Autowired
    public FileApplicationService(FilePolicy filePolicy,
                                  FileMetadataGateway metadataGateway,
                                  FileStorageGateway storageGateway) {
        this(filePolicy, metadataGateway, storageGateway, Clock.systemUTC());
    }

    FileApplicationService(FilePolicy filePolicy,
                           FileMetadataGateway metadataGateway,
                           FileStorageGateway storageGateway,
                           Clock clock) {
        this.filePolicy = filePolicy;
        this.metadataGateway = metadataGateway;
        this.storageGateway = storageGateway;
        this.clock = clock;
    }

    @Transactional
    public FileMetadata upload(String originalFilename, String contentType, long size, InputStream inputStream) {
        Long tenantId = TenantContextHolder.require().tenantId();
        Long userId = TenantContextHolder.require().userId();
        var validatedFile = filePolicy.validate(originalFilename, contentType, size);
        Instant now = clock.instant();
        FileMetadata uploading = FileMetadata.uploading(
                null,
                tenantId,
                validatedFile.filename(),
                validatedFile.contentType(),
                validatedFile.extension(),
                now,
                userId,
                0L);

        FileMetadata created = metadataGateway.createUploading(uploading);
        long id = created.id();
        FileMetadata persistedUploading = FileMetadata.uploading(
                id, tenantId, validatedFile.filename(), validatedFile.contentType(), validatedFile.extension(), now, userId, 0L);
        try {
            var stored = storageGateway.store(tenantId, inputStream);
            try {
                metadataGateway.markAvailable(id, stored.storageType(), stored.objectKey(), stored.size(), 0L);
            } catch (RuntimeException ex) {
                safeDeleteStoredObject(tenantId, stored.objectKey());
                throw new BizException(ErrorCode.FILE_STORAGE_FAILED);
            }
            return persistedUploading.markAvailable(stored.storageType(), stored.objectKey(), stored.size());
        } catch (RuntimeException ex) {
            cleanupUploading(id);
            if (ex instanceof BizException bizException) {
                throw bizException;
            }
            throw new BizException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    public FileMetadata get(Long fileId) {
        Long tenantId = TenantContextHolder.require().tenantId();
        return metadataGateway.findAvailableById(fileId)
                .filter(metadata -> tenantId.equals(metadata.tenantId()))
                .orElseThrow(() -> new BizException(ErrorCode.FILE_NOT_FOUND));
    }

    public PageResponse<FileMetadata> search(FileQuery query) {
        return metadataGateway.search(query);
    }

    public InputStream openContent(Long fileId) {
        FileMetadata metadata = get(fileId);
        try {
            return storageGateway.load(metadata.tenantId(), metadata.objectKey());
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.FILE_READ_FAILED);
        }
    }

    @Transactional
    public void delete(Long fileId) {
        Long tenantId = TenantContextHolder.require().tenantId();
        Long userId = TenantContextHolder.require().userId();
        FileMetadata metadata = metadataGateway.findDeletableById(fileId)
                .orElseThrow(() -> new BizException(ErrorCode.FILE_NOT_FOUND));
        if (!tenantId.equals(metadata.tenantId())) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }

        try {
            storageGateway.delete(tenantId, metadata.objectKey());
            metadataGateway.logicallyDelete(fileId, userId, metadata.version());
        } catch (RuntimeException ex) {
            try {
                metadataGateway.markDeleteFailed(fileId, metadata.version());
            } catch (RuntimeException ignored) {
                // best effort only
            }
            if (ex instanceof BizException bizException) {
                throw bizException;
            }
            throw new BizException(ErrorCode.FILE_DELETE_FAILED);
        }
    }

    private void cleanupUploading(long id) {
        try {
            metadataGateway.removeUploading(id);
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    private void safeDeleteStoredObject(Long tenantId, String objectKey) {
        try {
            storageGateway.delete(tenantId, objectKey);
        } catch (RuntimeException ignored) {
            // best effort only
        }
    }
}
