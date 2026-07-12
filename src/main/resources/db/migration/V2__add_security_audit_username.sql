ALTER TABLE security_audit_log
    ADD COLUMN username VARCHAR(128) NULL AFTER user_id;
