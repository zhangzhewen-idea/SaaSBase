package com.saasbase.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "登录状态已失效"),
    AUTH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "登录状态已失效"),
    AUTH_TENANT_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "租户会话已失效"),
    TENANT_NOT_FOUND(HttpStatus.NOT_FOUND, "租户不存在"),
    TENANT_DISABLED(HttpStatus.FORBIDDEN, "租户已停用"),
    TENANT_CODE_CONFLICT(HttpStatus.CONFLICT, "租户编码已存在"),
    TENANT_STATUS_CONFLICT(HttpStatus.CONFLICT, "租户状态冲突"),
    TENANT_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "租户已被并发修改"),
    IAM_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "权限不足"),
    IAM_DATA_SCOPE_DENIED(HttpStatus.FORBIDDEN, "数据范围不足"),
    IAM_USERNAME_CONFLICT(HttpStatus.CONFLICT, "用户名已存在"),
    IAM_PERMISSION_TEMPLATE_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "权限模板缺失"),
    TENANT_INITIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "租户初始化失败"),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT, "资源冲突"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务端错误");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
