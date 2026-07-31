package com.teamproject.report.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * v7-2 AI 주간 리포트 저장용 엔티티 (M5).
 * 주간 리포트의 개별 revision 결과를 불변 데이터 형태로 보관한다.
 */
@Entity
@Table(
    name = "ai_weekly_report_revision",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_ai_weekly_report_revision_group_period_lang_rev",
        columnNames = {"group_id", "period_from", "period_to_exclusive", "language", "revision"}
    )
)
public class AiWeeklyReportRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to_exclusive", nullable = false)
    private LocalDate periodToExclusive;

    @Column(name = "language", nullable = false, length = 8)
    private String language;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "analysis_mode", nullable = false, length = 20)
    private String analysisMode;

    @Column(name = "source_fingerprint", nullable = false, length = 64)
    private String sourceFingerprint;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "LONGTEXT")
    private String snapshotJson;

    @Column(name = "analysis_json", nullable = false, columnDefinition = "LONGTEXT")
    private String analysisJson;

    @Column(name = "prompt_version", nullable = false, length = 80)
    private String promptVersion;

    @Column(name = "model", length = 120)
    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AiWeeklyReportRevision() {}

    public AiWeeklyReportRevision(
            Long groupId,
            LocalDate periodFrom,
            LocalDate periodToExclusive,
            String language,
            int revision,
            String status,
            String analysisMode,
            String sourceFingerprint,
            String snapshotJson,
            String analysisJson,
            String promptVersion,
            String model,
            Integer inputTokens,
            Integer outputTokens,
            LocalDateTime generatedAt,
            LocalDateTime createdAt
    ) {
        this.groupId = groupId;
        this.periodFrom = periodFrom;
        this.periodToExclusive = periodToExclusive;
        this.language = language;
        this.revision = revision;
        this.status = status;
        this.analysisMode = analysisMode;
        this.sourceFingerprint = sourceFingerprint;
        this.snapshotJson = snapshotJson;
        this.analysisJson = analysisJson;
        this.promptVersion = promptVersion;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.generatedAt = generatedAt;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public LocalDate getPeriodFrom() { return periodFrom; }
    public LocalDate getPeriodToExclusive() { return periodToExclusive; }
    public String getLanguage() { return language; }
    public int getRevision() { return revision; }
    public String getStatus() { return status; }
    public String getAnalysisMode() { return analysisMode; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getAnalysisJson() { return analysisJson; }
    public String getPromptVersion() { return promptVersion; }
    public String getModel() { return model; }
    public Integer getInputTokens() { return inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
