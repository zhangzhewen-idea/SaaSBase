RENAME TABLE file_object TO file_metadata;

ALTER TABLE file_metadata
    CHANGE COLUMN filename original_filename VARCHAR(255) NOT NULL,
    ADD COLUMN extension VARCHAR(32) NOT NULL DEFAULT '' AFTER original_filename,
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE' AFTER extension,
    MODIFY COLUMN storage_type VARCHAR(32) NOT NULL COMMENT '存储类型：LOCAL',
    DROP INDEX uk_file_object_tenant_key,
    DROP INDEX idx_file_object_tenant_time,
    ADD UNIQUE KEY uk_file_metadata_tenant_key (tenant_id, object_key),
    ADD KEY idx_file_metadata_tenant_time (tenant_id, deleted, created_at),
    ADD KEY idx_file_metadata_tenant_type_time (tenant_id, deleted, content_type, created_at);
