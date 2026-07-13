package com.saasbase.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "登录状态已失效"),
    AUTH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "登录状态已失效"),
    AUTH_TENANT_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "租户会话已失效"),
    AUTH_USER_DISABLED(HttpStatus.FORBIDDEN, "用户已禁用"),
    AUTH_USER_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "用户会话已过期"),
    AUTH_PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "需要修改密码"),
    TENANT_NOT_FOUND(HttpStatus.NOT_FOUND, "租户不存在"),
    TENANT_DISABLED(HttpStatus.FORBIDDEN, "租户已停用"),
    TENANT_CODE_CONFLICT(HttpStatus.CONFLICT, "租户编码已存在"),
    TENANT_STATUS_CONFLICT(HttpStatus.CONFLICT, "租户状态冲突"),
    TENANT_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "租户已被并发修改"),
    IAM_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "权限不足"),
    IAM_DATA_SCOPE_DENIED(HttpStatus.FORBIDDEN, "数据范围不足"),
    IAM_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "用户不存在"),
    IAM_USERNAME_CONFLICT(HttpStatus.CONFLICT, "用户名已存在"),
    IAM_USER_STATUS_CONFLICT(HttpStatus.CONFLICT, "用户状态冲突"),
    IAM_USER_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "用户已被并发修改"),
    IAM_DEPARTMENT_NOT_FOUND(HttpStatus.BAD_REQUEST, "部门不存在"),
    IAM_DEPARTMENT_DISABLED(HttpStatus.CONFLICT, "部门已禁用"),
    IAM_DEPARTMENT_CODE_CONFLICT(HttpStatus.CONFLICT, "部门编码已存在"),
    IAM_DEPARTMENT_STATUS_CONFLICT(HttpStatus.CONFLICT, "部门状态冲突"),
    IAM_DEPARTMENT_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "部门已被并发修改"),
    IAM_DEPARTMENT_CYCLE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "禁止形成部门环"),
    IAM_DEPARTMENT_DEPTH_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "部门层级超过限制"),
    IAM_DEPARTMENT_NOT_EMPTY(HttpStatus.CONFLICT, "部门下存在子部门或成员"),
    IAM_DEPARTMENT_ROOT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "根部门不允许此操作"),
    IAM_ROLE_NOT_FOUND(HttpStatus.BAD_REQUEST, "角色不存在"),
    IAM_ROLE_DISABLED(HttpStatus.CONFLICT, "角色已禁用"),
    IAM_CROSS_TENANT_REFERENCE(HttpStatus.FORBIDDEN, "禁止跨租户引用"),
    IAM_LAST_TENANT_ADMIN_PROTECTED(HttpStatus.CONFLICT, "不能操作租户最后一名管理员"),
    IAM_SELF_OPERATION_FORBIDDEN(HttpStatus.CONFLICT, "禁止对当前用户执行此操作"),
    IAM_PERMISSION_TEMPLATE_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "权限模板缺失"),
    TENANT_INITIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "租户初始化失败"),
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
