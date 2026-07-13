ALTER TABLE iam_dept
    ADD COLUMN sort_order BIGINT NOT NULL DEFAULT 0 AFTER dept_name,
    ADD KEY idx_iam_dept_tenant_status (tenant_id, status),
    ADD KEY idx_iam_dept_tenant_sort (tenant_id, parent_id, sort_order, id);
