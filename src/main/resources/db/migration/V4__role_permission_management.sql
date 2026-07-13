ALTER TABLE iam_role
    ADD COLUMN role_type VARCHAR(32) NOT NULL DEFAULT 'CUSTOM',
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN data_scope VARCHAR(32) NOT NULL DEFAULT 'SELF',
    ADD KEY idx_iam_role_tenant_status (tenant_id, status);

ALTER TABLE iam_user
    ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;

UPDATE iam_role
SET role_type = 'BUILT_IN'
WHERE role_code = 'TENANT_ADMIN';

INSERT INTO iam_role (id, tenant_id, role_code, role_name, role_type, status, data_scope, created_at, updated_at)
SELECT 40001, 0, 'TENANT_ADMIN', 'Tenant Admin', 'BUILT_IN', 'ACTIVE', 'ALL', NOW(6), NOW(6)
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM iam_role
    WHERE tenant_id = 0
      AND role_code = 'TENANT_ADMIN'
);

INSERT INTO iam_permission (id, permission_code, permission_name, permission_type, created_at) VALUES
    (41001, 'iam:role:read', 'Read roles', 'API', NOW(6)),
    (41002, 'iam:role:create', 'Create roles', 'API', NOW(6)),
    (41003, 'iam:role:update', 'Update roles', 'API', NOW(6)),
    (41004, 'iam:role:delete', 'Delete roles', 'API', NOW(6)),
    (41005, 'iam:role:authorize', 'Authorize roles', 'API', NOW(6)),
    (41006, 'iam:user-role:read', 'Read user roles', 'API', NOW(6)),
    (41007, 'iam:user-role:assign', 'Assign user roles', 'API', NOW(6)),
    (41008, 'iam:permission:read', 'Read permissions', 'API', NOW(6));
