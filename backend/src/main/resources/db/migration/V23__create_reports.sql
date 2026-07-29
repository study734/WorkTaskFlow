CREATE TABLE reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    requested_by_member_id BIGINT NULL,
    type VARCHAR(20) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    language VARCHAR(5) NOT NULL,
    revision INT NOT NULL DEFAULT 1,
    trigger_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL,
    metrics_json LONGTEXT NOT NULL,
    ai_summary_json LONGTEXT NULL,
    model VARCHAR(80) NULL,
    prompt_version VARCHAR(30) NOT NULL,
    schema_version VARCHAR(30) NOT NULL,
    input_tokens INT NULL,
    output_tokens INT NULL,
    total_tokens INT NULL,
    failure_code VARCHAR(80) NULL,
    generation_started_at DATETIME(6) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    generated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reports_group_type_period_language_revision
        UNIQUE (group_id, type, period_start, period_end, language, revision),
    CONSTRAINT fk_reports_group
        FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_reports_requested_member
        FOREIGN KEY (requested_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;
