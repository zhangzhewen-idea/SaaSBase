package com.saasbase.tenant.domain;

import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;

public final class Tenant {
    private static final int MAX_TENANT_NAME_LENGTH = 128;

    private final Long id;
    private final String tenantCode;
    private String tenantName;
    private TenantStatus status;
    private long sessionVersion;
    private final long version;

    private Tenant(
            Long id,
            String tenantCode,
            String tenantName,
            TenantStatus status,
            long sessionVersion,
            long version) {
        this.id = id;
        this.tenantCode = requireNonBlank(tenantCode, "tenantCode");
        this.tenantName = validateTenantName(tenantName);
        this.status = requireStatus(status);
        this.sessionVersion = requireNonNegative(sessionVersion, "sessionVersion");
        this.version = requireNonNegative(version, "version");
    }

    public static Tenant create(String tenantCode, String tenantName) {
        return new Tenant(null, tenantCode, tenantName, TenantStatus.ACTIVE, 0, 0);
    }

    public static Tenant reconstitute(
            Long id,
            String tenantCode,
            String tenantName,
            TenantStatus status,
            long sessionVersion,
            long version) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        return new Tenant(id, tenantCode, tenantName, status, sessionVersion, version);
    }

    public void rename(String tenantName) {
        this.tenantName = validateTenantName(tenantName);
    }

    public void disable() {
        if (status != TenantStatus.ACTIVE) {
            throw new BizException(ErrorCode.TENANT_STATUS_CONFLICT);
        }
        status = TenantStatus.DISABLED;
        sessionVersion++;
    }

    public void enable() {
        if (status != TenantStatus.DISABLED) {
            throw new BizException(ErrorCode.TENANT_STATUS_CONFLICT);
        }
        status = TenantStatus.ACTIVE;
    }

    public TenantAuthState authState() {
        return new TenantAuthState(id, status, sessionVersion);
    }

    public Long id() {
        return id;
    }

    public String tenantCode() {
        return tenantCode;
    }

    public String tenantName() {
        return tenantName;
    }

    public TenantStatus status() {
        return status;
    }

    public long sessionVersion() {
        return sessionVersion;
    }

    public long version() {
        return version;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String validateTenantName(String tenantName) {
        String normalizedName = requireNonBlank(tenantName, "tenantName");
        if (normalizedName.length() > MAX_TENANT_NAME_LENGTH) {
            throw new IllegalArgumentException("tenantName must not exceed 128 characters");
        }
        return normalizedName;
    }

    private static TenantStatus requireStatus(TenantStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        return status;
    }

    private static long requireNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }
}
