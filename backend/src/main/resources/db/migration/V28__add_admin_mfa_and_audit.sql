CREATE TABLE admin_mfa_credentials (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    encrypted_secret VARCHAR(1000) NOT NULL,
    recovery_code_hashes TEXT NULL,
    enabled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_mfa_credentials_user UNIQUE (user_id),
    CONSTRAINT fk_admin_mfa_credentials_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE admin_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NULL,
    http_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(500) NOT NULL,
    http_status INT NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    request_id VARCHAR(80) NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_admin_audit_logs_occurred (occurred_at, id),
    INDEX idx_admin_audit_logs_actor (actor_user_id, occurred_at),
    CONSTRAINT fk_admin_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
) ENGINE = InnoDB;
