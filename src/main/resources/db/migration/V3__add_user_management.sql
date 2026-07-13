ALTER TABLE iam_user
    ADD COLUMN phone VARCHAR(32) NULL AFTER display_name,
    ADD COLUMN primary_department_id BIGINT NULL AFTER phone,
    ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 1 AFTER status,
    ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0 AFTER must_change_password,
    ADD COLUMN last_login_at DATETIME(6) NULL AFTER session_version;

ALTER TABLE iam_user
    ADD KEY idx_iam_user_tenant_department (tenant_id, primary_department_id),
    ADD KEY idx_iam_user_tenant_phone (tenant_id, phone);

ALTER TABLE iam_dept
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' AFTER dept_name;

ALTER TABLE iam_role
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' AFTER role_name;

INSERT INTO iam_permission (id, permission_code, permission_name, permission_type, created_at) VALUES
    (900001, 'tenant:user:create', '创建用户', 'API', CURRENT_TIMESTAMP(6)),
    (900002, 'tenant:user:read', '查看用户', 'API', CURRENT_TIMESTAMP(6)),
    (900003, 'tenant:user:update', '更新用户', 'API', CURRENT_TIMESTAMP(6)),
    (900004, 'tenant:user:enable', '启用用户', 'API', CURRENT_TIMESTAMP(6)),
    (900005, 'tenant:user:disable', '禁用用户', 'API', CURRENT_TIMESTAMP(6)),
    (900006, 'tenant:user:reset-password', '重置用户密码', 'API', CURRENT_TIMESTAMP(6));
