ALTER TABLE work_groups
    ADD COLUMN paid_started_at DATETIME(6) NULL,
    ADD COLUMN paid_until DATETIME(6) NULL,
    ADD COLUMN next_billing_at DATETIME(6) NULL;
