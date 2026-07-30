ALTER TABLE users
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE refresh_tokens
    ADD COLUMN session_id VARCHAR(36) NULL,
    ADD COLUMN client_mode VARCHAR(10) NOT NULL DEFAULT 'WEB',
    ADD COLUMN absolute_expires_at DATETIME(6) NULL;

UPDATE refresh_tokens
SET session_id = UUID(),
    absolute_expires_at = expires_at
WHERE session_id IS NULL OR absolute_expires_at IS NULL;

ALTER TABLE refresh_tokens
    MODIFY session_id VARCHAR(36) NOT NULL,
    MODIFY absolute_expires_at DATETIME(6) NOT NULL,
    ADD INDEX idx_refresh_session (session_id);
