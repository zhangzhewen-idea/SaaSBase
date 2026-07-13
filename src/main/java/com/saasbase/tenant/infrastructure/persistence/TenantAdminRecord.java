package com.saasbase.tenant.infrastructure.persistence;

import java.time.LocalDateTime;

final class TenantAdminRecord {
    private Long id;
    private final Long tenantId;
    private final String code;
    private final String name;
    private final String passwordHash;
    private final LocalDateTime now;
    private final Long operatorId;

    TenantAdminRecord(Long tenantId, String code, String name, String passwordHash, LocalDateTime now, Long operatorId) {
        this.tenantId = tenantId; this.code = code; this.name = name;
        this.passwordHash = passwordHash; this.now = now; this.operatorId = operatorId;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long tenantId() { return tenantId; }
    public String code() { return code; }
    public String name() { return name; }
    public String passwordHash() { return passwordHash; }
    public LocalDateTime now() { return now; }
    public Long operatorId() { return operatorId; }
    @Override public String toString() { return "TenantAdminRecord[id=" + id + ", tenantId=" + tenantId + ", code=" + code + ", passwordHash=***]"; }
}
