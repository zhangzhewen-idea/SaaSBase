package com.saasbase.iam.domain;

import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;

import java.util.Objects;

public class IamUser {
    private final Long id;
    private final Long tenantId;
    private final String username;
    private String passwordHash;
    private UserStatus status;
    private boolean mustChangePassword;
    private long sessionVersion;

    public IamUser(Long id, Long tenantId, String username, String passwordHash, UserStatus status,
                   boolean mustChangePassword, long sessionVersion) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.username = requireText(username, "username");
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.status = Objects.requireNonNull(status, "status");
        this.mustChangePassword = mustChangePassword;
        this.sessionVersion = sessionVersion;
    }

    public void disable() {
        if (status != UserStatus.ACTIVE) {
            throw new BizException(ErrorCode.IAM_USER_STATUS_CONFLICT);
        }
        status = UserStatus.DISABLED;
        sessionVersion++;
    }

    public void enable() {
        if (status != UserStatus.DISABLED) {
            throw new BizException(ErrorCode.IAM_USER_STATUS_CONFLICT);
        }
        status = UserStatus.ACTIVE;
    }

    public void resetPassword(String encodedPassword) {
        passwordHash = requireText(encodedPassword, "encodedPassword");
        mustChangePassword = true;
        sessionVersion++;
    }

    public Long id() {
        return id;
    }

    public Long tenantId() {
        return tenantId;
    }

    public String username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public UserStatus status() {
        return status;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public long sessionVersion() {
        return sessionVersion;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
