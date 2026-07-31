CREATE TABLE ai_weekly_report_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    period_from DATE NOT NULL,
    period_to_exclusive DATE NOT NULL,
    language VARCHAR(8) NOT NULL,
    revision INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    analysis_mode VARCHAR(20) NOT NULL,
    source_fingerprint VARCHAR(64) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    analysis_json LONGTEXT NOT NULL,
    prompt_version VARCHAR(80) NOT NULL,
    model VARCHAR(120) NULL,
    input_tokens INT NULL,
    output_tokens INT NULL,
    generated_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ai_weekly_report_revision_group_period_lang_rev
        UNIQUE (group_id, period_from, period_to_exclusive, language, revision)
) ENGINE = InnoDB;

CREATE INDEX idx_ai_weekly_report_revision_source_fingerprint
    ON ai_weekly_report_revision (group_id, period_from, period_to_exclusive, source_fingerprint);
