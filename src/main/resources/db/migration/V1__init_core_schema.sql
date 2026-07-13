CREATE TABLE tenant (
    id BIGINT PRIMARY KEY COMMENT '租户主键',
    tenant_code VARCHAR(64) NOT NULL COMMENT '租户编码',
    tenant_name VARCHAR(128) NOT NULL COMMENT '租户名称',
    status VARCHAR(32) NOT NULL COMMENT '租户状态：ACTIVE-正常，DISABLED-停用',
    created_at DATETIME(6) NOT NULL COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人用户 ID',
    updated_at DATETIME(6) NOT NULL COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人用户 ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
    deleted_at DATETIME(6) NULL COMMENT '删除时间',
    deleted_by BIGINT NULL COMMENT '删除人用户 ID',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    UNIQUE KEY uk_tenant_code (tenant_code) COMMENT '租户编码唯一索引'
) COMMENT = '租户信息表';

CREATE TABLE iam_user (
    id BIGINT PRIMARY KEY COMMENT '用户主键',
    tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希值',
    display_name VARCHAR(128) NOT NULL COMMENT '用户显示名称',
    status VARCHAR(32) NOT NULL COMMENT '用户状态：ACTIVE-正常，DISABLED-停用，LOCKED-锁定',
    created_at DATETIME(6) NOT NULL COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人用户 ID',
    updated_at DATETIME(6) NOT NULL COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人用户 ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
    deleted_at DATETIME(6) NULL COMMENT '删除时间',
    deleted_by BIGINT NULL COMMENT '删除人用户 ID',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    UNIQUE KEY uk_iam_user_tenant_username (tenant_id, username) COMMENT '租户内用户名唯一索引',
    KEY idx_iam_user_tenant_status (tenant_id, status) COMMENT '按租户和状态查询用户索引'
) COMMENT = 'IAM 用户表';

CREATE TABLE iam_dept (
    id BIGINT PRIMARY KEY COMMENT '部门主键',
    tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    parent_id BIGINT NULL COMMENT '上级部门 ID，根部门为空',
    dept_code VARCHAR(64) NOT NULL COMMENT '部门编码',
    dept_name VARCHAR(128) NOT NULL COMMENT '部门名称',
    created_at DATETIME(6) NOT NULL COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人用户 ID',
    updated_at DATETIME(6) NOT NULL COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人用户 ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
    deleted_at DATETIME(6) NULL COMMENT '删除时间',
    deleted_by BIGINT NULL COMMENT '删除人用户 ID',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    UNIQUE KEY uk_iam_dept_tenant_code (tenant_id, dept_code) COMMENT '租户内部门编码唯一索引',
    KEY idx_iam_dept_tenant_parent (tenant_id, parent_id) COMMENT '按租户和上级部门查询索引'
) COMMENT = 'IAM 部门表';

CREATE TABLE iam_role (
    id BIGINT PRIMARY KEY COMMENT '角色主键',
    tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(128) NOT NULL COMMENT '角色名称',
    created_at DATETIME(6) NOT NULL COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人用户 ID',
    updated_at DATETIME(6) NOT NULL COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人用户 ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
    deleted_at DATETIME(6) NULL COMMENT '删除时间',
    deleted_by BIGINT NULL COMMENT '删除人用户 ID',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    UNIQUE KEY uk_iam_role_tenant_code (tenant_id, role_code) COMMENT '租户内角色编码唯一索引'
) COMMENT = 'IAM 角色表';

CREATE TABLE iam_permission (
    id BIGINT PRIMARY KEY COMMENT '权限主键',
    permission_code VARCHAR(128) NOT NULL COMMENT '权限编码',
    permission_name VARCHAR(128) NOT NULL COMMENT '权限名称',
    permission_type VARCHAR(32) NOT NULL COMMENT '权限类型：API-接口，MENU-菜单，OPERATION-操作',
    created_at DATETIME(6) NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_iam_permission_code (permission_code) COMMENT '权限编码唯一索引'
) COMMENT = 'IAM 权限定义表';

CREATE TABLE iam_user_role (
    tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    PRIMARY KEY (tenant_id, user_id, role_id)
) COMMENT = 'IAM 用户角色关联表';

CREATE TABLE iam_role_permission (
    tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    permission_id BIGINT NOT NULL COMMENT '权限 ID',
    PRIMARY KEY (tenant_id, role_id, permission_id)
) COMMENT = 'IAM 角色权限关联表';

CREATE TABLE security_audit_log (
    id BIGINT PRIMARY KEY COMMENT '安全审计日志主键',
    tenant_id BIGINT NULL COMMENT '关联租户 ID，登录失败时可为空',
    user_id BIGINT NULL COMMENT '关联用户 ID，登录失败时可为空',
    event_type VARCHAR(64) NOT NULL COMMENT '安全事件类型：LOGIN-登录成功，LOGIN_FAILURE-登录失败，LOGOUT-退出登录，TOKEN_REFRESH-刷新令牌，PASSWORD_CHANGE-修改密码，PASSWORD_RESET-重置密码',
    result VARCHAR(32) NOT NULL COMMENT '安全事件处理结果：SUCCESS-成功，FAILURE-失败',
    trace_id VARCHAR(64) NULL COMMENT '请求链路追踪 ID',
    client_ip VARCHAR(64) NULL COMMENT '客户端 IP 地址',
    user_agent VARCHAR(255) NULL COMMENT '客户端 User-Agent',
    created_at DATETIME(6) NOT NULL COMMENT '事件发生时间',
    KEY idx_security_audit_tenant_time (tenant_id, created_at) COMMENT '按租户和时间查询安全审计日志索引',
    KEY idx_security_audit_user_time (user_id, created_at) COMMENT '按用户和时间查询安全审计日志索引'
) COMMENT = '安全审计日志表';

CREATE TABLE admin_operation_audit_log (
    id BIGINT PRIMARY KEY COMMENT '管理操作审计日志主键',
    tenant_id BIGINT NULL COMMENT '关联租户 ID',
    user_id BIGINT NULL COMMENT '操作用户 ID',
    operation_type VARCHAR(64) NOT NULL COMMENT '操作类型：CREATE-创建，UPDATE-更新，DELETE-删除，ENABLE-启用，DISABLE-停用，GRANT-授权，REVOKE-撤权',
    resource_type VARCHAR(64) NOT NULL COMMENT '操作资源类型：TENANT-租户，USER-用户，DEPT-部门，ROLE-角色，PERMISSION-权限，SYSTEM_CONFIG-系统配置，FILE_OBJECT-文件对象',
    resource_id VARCHAR(128) NULL COMMENT '操作资源标识',
    trace_id VARCHAR(64) NULL COMMENT '请求链路追踪 ID',
    created_at DATETIME(6) NOT NULL COMMENT '操作发生时间',
    KEY idx_admin_audit_tenant_time (tenant_id, created_at) COMMENT '按租户和时间查询管理操作审计日志索引',
    KEY idx_admin_audit_user_time (user_id, created_at) COMMENT '按用户和时间查询管理操作审计日志索引'
) COMMENT = '管理操作审计日志表';

CREATE TABLE system_config (
    id BIGINT PRIMARY KEY COMMENT '系统配置主键',
    config_key VARCHAR(128) NOT NULL COMMENT '配置键',
    config_value VARCHAR(1024) NOT NULL COMMENT '配置值',
    description VARCHAR(255) NULL COMMENT '配置说明',
    created_at DATETIME(6) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(6) NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_system_config_key (config_key) COMMENT '配置键唯一索引'
) COMMENT = '系统配置表';

CREATE TABLE file_object (
    id BIGINT PRIMARY KEY COMMENT '文件对象主键',
    tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    storage_type VARCHAR(32) NOT NULL COMMENT '存储类型：local-本地文件系统，oss-阿里云 OSS（预留）',
    object_key VARCHAR(128) NOT NULL COMMENT '对象存储键',
    filename VARCHAR(255) NOT NULL COMMENT '原始文件名',
    content_type VARCHAR(128) NOT NULL COMMENT '文件 MIME 类型',
    size BIGINT NOT NULL COMMENT '文件大小，单位为字节',
    created_at DATETIME(6) NOT NULL COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人用户 ID',
    updated_at DATETIME(6) NOT NULL COMMENT '更新时间',
    updated_by BIGINT NULL COMMENT '更新人用户 ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
    deleted_at DATETIME(6) NULL COMMENT '删除时间',
    deleted_by BIGINT NULL COMMENT '删除人用户 ID',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    UNIQUE KEY uk_file_object_tenant_key (tenant_id, object_key) COMMENT '租户内对象存储键唯一索引',
    KEY idx_file_object_tenant_time (tenant_id, created_at) COMMENT '按租户和创建时间查询文件对象索引'
) COMMENT = '文件对象元数据表';
