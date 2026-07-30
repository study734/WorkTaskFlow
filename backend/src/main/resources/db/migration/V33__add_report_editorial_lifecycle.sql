ALTER TABLE reports
    ADD COLUMN ai_context_json LONGTEXT NULL AFTER metrics_json,
    ADD COLUMN reference_index_json LONGTEXT NULL AFTER ai_context_json,
    ADD COLUMN evidence_json LONGTEXT NULL AFTER reference_index_json,
    ADD COLUMN editorial_json LONGTEXT NULL AFTER ai_summary_json,
    ADD COLUMN publication_status VARCHAR(20) NOT NULL DEFAULT 'LEGACY' AFTER status,
    ADD COLUMN editor_version BIGINT NOT NULL DEFAULT 0 AFTER publication_status,
    ADD COLUMN source_report_id BIGINT NULL AFTER editor_version,
    ADD COLUMN finalized_at DATETIME(6) NULL AFTER generated_at,
    ADD COLUMN finalized_by_member_id BIGINT NULL AFTER finalized_at,
    ADD INDEX idx_reports_series_revision (
        group_id, type, period_start, period_end, language, revision
    ),
    ADD INDEX idx_reports_publication (
        group_id, publication_status, period_end
    ),
    ADD CONSTRAINT fk_reports_source_report
        FOREIGN KEY (source_report_id) REFERENCES reports (id),
    ADD CONSTRAINT fk_reports_finalized_member
        FOREIGN KEY (finalized_by_member_id) REFERENCES group_members (id);
