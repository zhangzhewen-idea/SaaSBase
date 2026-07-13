package com.saasbase.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    AUTH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "登录状态已失效"),
    TENANT_NOT_FOUND(HttpStatus.NOT_FOUND, "租户不存在"),
    IAM_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "权限不足"),
    IAM_DATA_SCOPE_DENIED(HttpStatus.FORBIDDEN, "数据范围不足"),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT, "资源冲突"),
    FILE_INVALID(HttpStatus.BAD_REQUEST, "文件无效"),
    FILE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "文件大小超过限制"),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的文件类型"),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "文件不存在"),
    FILE_STATE_INVALID(HttpStatus.CONFLICT, "文件状态无效"),
    FILE_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "文件存储失败"),
    FILE_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "文件读取失败"),
    FILE_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "文件删除失败"),
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
