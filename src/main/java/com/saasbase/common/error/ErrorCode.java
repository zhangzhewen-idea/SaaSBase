package com.saasbase.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    AUTH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "登录状态已失效"),
    AUTH_USER_DISABLED(HttpStatus.FORBIDDEN, "用户已禁用"),
    AUTH_USER_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "用户会话已过期"),
    AUTH_PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "需要修改密码"),
    TENANT_NOT_FOUND(HttpStatus.NOT_FOUND, "租户不存在"),
    IAM_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "权限不足"),
    IAM_DATA_SCOPE_DENIED(HttpStatus.FORBIDDEN, "数据范围不足"),
    IAM_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "用户不存在"),
    IAM_USERNAME_CONFLICT(HttpStatus.CONFLICT, "用户名已存在"),
    IAM_USER_STATUS_CONFLICT(HttpStatus.CONFLICT, "用户状态冲突"),
    IAM_USER_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "用户已被并发修改"),
    IAM_DEPARTMENT_NOT_FOUND(HttpStatus.BAD_REQUEST, "部门不存在"),
    IAM_DEPARTMENT_DISABLED(HttpStatus.CONFLICT, "部门已禁用"),
    IAM_ROLE_NOT_FOUND(HttpStatus.BAD_REQUEST, "角色不存在"),
    IAM_ROLE_DISABLED(HttpStatus.CONFLICT, "角色已禁用"),
    IAM_CROSS_TENANT_REFERENCE(HttpStatus.FORBIDDEN, "禁止跨租户引用"),
    IAM_LAST_TENANT_ADMIN_PROTECTED(HttpStatus.CONFLICT, "不能操作租户最后一名管理员"),
    IAM_SELF_OPERATION_FORBIDDEN(HttpStatus.CONFLICT, "禁止对当前用户执行此操作"),
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
