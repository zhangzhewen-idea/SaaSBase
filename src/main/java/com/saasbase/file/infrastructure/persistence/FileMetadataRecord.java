package com.saasbase.file.infrastructure.persistence;

import com.saasbase.file.domain.FileStatus;

import java.time.Instant;

public class FileMetadataRecord {
    private Long id;
    private Long tenantId;
    private String storageType;
    private String objectKey;
    private String originalFilename;
    private String contentType;
    private String extension;
    private Long size;
    private FileStatus status;
    private Instant createdAt;
    private Long createdBy;
    private Instant updatedAt;
    private Long updatedBy;
    private Boolean deleted;
    private Instant deletedAt;
    private Long deletedBy;
    private Long version;

    public FileMetadataRecord() {
    }

    public FileMetadataRecord(
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
        this.id = id;
        this.tenantId = tenantId;
        this.storageType = storageType;
        this.objectKey = objectKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.extension = extension;
        this.size = size;
        this.status = status;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.deleted = deleted;
        this.deletedAt = deletedAt;
        this.deletedBy = deletedBy;
        this.version = version;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getStorageType() { return storageType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public FileStatus getStatus() { return status; }
    public void setStatus(FileStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
