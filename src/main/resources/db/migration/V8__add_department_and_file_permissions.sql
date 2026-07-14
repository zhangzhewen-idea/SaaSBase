INSERT INTO iam_permission (id, permission_code, permission_name, permission_type, created_at) VALUES
    (900007, 'tenant:dept:read', '查看部门', 'API', CURRENT_TIMESTAMP(6)),
    (900008, 'tenant:dept:create', '创建部门', 'API', CURRENT_TIMESTAMP(6)),
    (900009, 'tenant:dept:update', '更新部门', 'API', CURRENT_TIMESTAMP(6)),
    (900010, 'tenant:dept:move', '移动部门', 'API', CURRENT_TIMESTAMP(6)),
    (900011, 'tenant:dept:disable', '禁用部门', 'API', CURRENT_TIMESTAMP(6)),
    (900012, 'tenant:dept:enable', '启用部门', 'API', CURRENT_TIMESTAMP(6)),
    (900013, 'tenant:dept:delete', '删除部门', 'API', CURRENT_TIMESTAMP(6)),
    (900014, 'tenant:dept:member:read', '查看部门成员', 'API', CURRENT_TIMESTAMP(6)),
    (900015, 'tenant:file:write', '上传文件', 'API', CURRENT_TIMESTAMP(6)),
    (900016, 'tenant:file:read', '查看文件', 'API', CURRENT_TIMESTAMP(6)),
    (900017, 'tenant:file:delete', '删除文件', 'API', CURRENT_TIMESTAMP(6));
