ALTER TABLE tenant
    ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0 COMMENT '租户认证会话版本' AFTER status,
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE iam_user MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE iam_role MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

INSERT INTO iam_permission (id, permission_code, permission_name, permission_type, created_at)
VALUES (900000000000000101, 'platform:tenant:create', '创建租户', 'API', CURRENT_TIMESTAMP(6)),
       (900000000000000102, 'platform:tenant:read', '查看租户', 'API', CURRENT_TIMESTAMP(6)),
       (900000000000000103, 'platform:tenant:update', '更新租户', 'API', CURRENT_TIMESTAMP(6)),
       (900000000000000104, 'platform:tenant:enable', '启用租户', 'API', CURRENT_TIMESTAMP(6)),
       (900000000000000105, 'platform:tenant:disable', '停用租户', 'API', CURRENT_TIMESTAMP(6)),
       (900000000000000106, 'tenant:profile:read', '查看租户资料', 'API', CURRENT_TIMESTAMP(6));
