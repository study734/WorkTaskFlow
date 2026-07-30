ALTER TABLE group_subscriptions
    ADD COLUMN last_billing_attempt_at DATETIME(6) NULL AFTER cancelled_at,
    ADD COLUMN past_due_since DATETIME(6) NULL AFTER last_billing_attempt_at,
    ADD COLUMN consecutive_failures INT NOT NULL DEFAULT 0 AFTER past_due_since;

CREATE INDEX idx_group_subscriptions_past_due
    ON group_subscriptions (status, past_due_since);

CREATE TABLE subscription_consents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    terms_version VARCHAR(30) NOT NULL,
    refund_policy_version VARCHAR(30) NOT NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    accepted_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_subscription_consents_subscription (subscription_id, accepted_at),
    CONSTRAINT fk_subscription_consents_subscription FOREIGN KEY (subscription_id) REFERENCES group_subscriptions (id),
    CONSTRAINT fk_subscription_consents_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE scheduled_job_locks (
    name VARCHAR(80) NOT NULL,
    locked_until DATETIME(6) NOT NULL,
    locked_at DATETIME(6) NULL,
    locked_by VARCHAR(120) NULL,
    PRIMARY KEY (name)
) ENGINE = InnoDB;

INSERT INTO scheduled_job_locks (name, locked_until) VALUES
    ('subscription-lifecycle', '1970-01-01 00:00:00'),
    ('subscription-billing', '1970-01-01 00:00:00'),
    ('report-mail', '1970-01-01 00:00:00'),
    ('report-mail-retry', '1970-01-01 00:00:00');

ALTER TABLE report_deliveries
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN last_attempt_at DATETIME(6) NULL AFTER retry_count,
    ADD COLUMN next_retry_at DATETIME(6) NULL AFTER last_attempt_at;

CREATE INDEX idx_report_deliveries_retry
    ON report_deliveries (status, next_retry_at);
