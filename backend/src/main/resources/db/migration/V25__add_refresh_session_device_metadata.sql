ALTER TABLE refresh_tokens
    ADD COLUMN device_id VARCHAR(64) NOT NULL DEFAULT 'unknown',
    ADD COLUMN device_name VARCHAR(100) NOT NULL DEFAULT '알 수 없는 기기',
    ADD COLUMN user_agent VARCHAR(500) NOT NULL DEFAULT 'unknown',
    ADD COLUMN ip_address VARCHAR(64) NOT NULL DEFAULT 'unknown',
    ADD COLUMN created_at DATETIME(6) NULL,
    ADD COLUMN last_used_at DATETIME(6) NULL;

UPDATE refresh_tokens
SET created_at = COALESCE(created_at, DATE_SUB(expires_at, INTERVAL 14 DAY)),
    last_used_at = COALESCE(last_used_at, revoked_at, DATE_SUB(expires_at, INTERVAL 14 DAY))
WHERE created_at IS NULL OR last_used_at IS NULL;

ALTER TABLE refresh_tokens
    MODIFY created_at DATETIME(6) NOT NULL,
    MODIFY last_used_at DATETIME(6) NOT NULL,
    ADD INDEX idx_refresh_tokens_device (user_id, device_id),
    ADD INDEX idx_refresh_tokens_cleanup (absolute_expires_at);
