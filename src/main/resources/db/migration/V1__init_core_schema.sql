CREATE TABLE tenant (
    id BIGINT PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    tenant_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_code)
);

CREATE TABLE iam_user (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_iam_user_tenant_username (tenant_id, username),
    KEY idx_iam_user_tenant_status (tenant_id, status)
);

CREATE TABLE iam_dept (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    dept_code VARCHAR(64) NOT NULL,
    dept_name VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_iam_dept_tenant_code (tenant_id, dept_code),
    KEY idx_iam_dept_tenant_parent (tenant_id, parent_id)
);

CREATE TABLE iam_role (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_iam_role_tenant_code (tenant_id, role_code)
);

CREATE TABLE iam_permission (
    id BIGINT PRIMARY KEY,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    permission_type VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_iam_permission_code (permission_code)
);

CREATE TABLE iam_user_role (
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, user_id, role_id)
);

CREATE TABLE iam_role_permission (
    tenant_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, role_id, permission_id)
);

CREATE TABLE security_audit_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NULL,
    user_id BIGINT NULL,
    event_type VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    trace_id VARCHAR(64) NULL,
    client_ip VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    KEY idx_security_audit_tenant_time (tenant_id, created_at),
    KEY idx_security_audit_user_time (user_id, created_at)
);

CREATE TABLE admin_operation_audit_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NULL,
    user_id BIGINT NULL,
    operation_type VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NULL,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    KEY idx_admin_audit_tenant_time (tenant_id, created_at),
    KEY idx_admin_audit_user_time (user_id, created_at)
);

CREATE TABLE system_config (
    id BIGINT PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL,
    config_value VARCHAR(1024) NOT NULL,
    description VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_system_config_key (config_key)
);

CREATE TABLE file_object (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    storage_type VARCHAR(32) NOT NULL,
    object_key VARCHAR(128) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_file_object_tenant_key (tenant_id, object_key),
    KEY idx_file_object_tenant_time (tenant_id, created_at)
);
