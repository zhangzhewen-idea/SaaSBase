package com.saasbase.tenant.infrastructure.persistence;

import com.saasbase.tenant.domain.TenantStatus;

import java.time.LocalDateTime;

public final class TenantRecord {
    private Long id;
    private final String tenantCode;
    private final String tenantName;
    private final TenantStatus status;
    private final Long sessionVersion;
    private final LocalDateTime createdAt;
    private final Long createdBy;
    private final LocalDateTime updatedAt;
    private final Long updatedBy;
    private final Boolean deleted;
    private final Long version;

    public TenantRecord(Long id, String tenantCode, String tenantName, TenantStatus status, Long sessionVersion,
                        LocalDateTime createdAt, Long createdBy, LocalDateTime updatedAt, Long updatedBy,
                        Boolean deleted, Long version) {
        this.id = id;
        this.tenantCode = tenantCode;
        this.tenantName = tenantName;
        this.status = status;
        this.sessionVersion = sessionVersion;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.deleted = deleted;
        this.version = version;
    }

    public Long id() { return id; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String tenantCode() { return tenantCode; }
    public String tenantName() { return tenantName; }
    public TenantStatus status() { return status; }
    public Long sessionVersion() { return sessionVersion; }
    public LocalDateTime createdAt() { return createdAt; }
    public Long createdBy() { return createdBy; }
    public LocalDateTime updatedAt() { return updatedAt; }
    public Long updatedBy() { return updatedBy; }
    public Boolean deleted() { return deleted; }
    public Long version() { return version; }
}
