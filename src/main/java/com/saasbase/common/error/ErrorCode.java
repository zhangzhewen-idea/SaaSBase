package com.saasbase.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    AUTH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "登录状态已失效"),
    TENANT_NOT_FOUND(HttpStatus.NOT_FOUND, "租户不存在"),
    IAM_ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "角色不存在"),
    IAM_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "用户不存在"),
    IAM_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "权限不足"),
    IAM_DATA_SCOPE_DENIED(HttpStatus.FORBIDDEN, "数据范围不足"),
    IAM_ROLE_CODE_CONFLICT(HttpStatus.CONFLICT, "角色编码已存在"),
    IAM_BUILT_IN_ROLE_PROTECTED(HttpStatus.CONFLICT, "内置角色不可修改"),
    IAM_LAST_TENANT_ADMIN(HttpStatus.CONFLICT, "不能移除最后一个租户管理员"),
    IAM_OPTIMISTIC_LOCK_CONFLICT(HttpStatus.CONFLICT, "数据已被其他操作修改，请刷新后重试"),
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
