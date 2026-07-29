package com.teamproject.report.domain;

import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 생성 성공 여부({@link Status})와 독자에게 공개되는 상태({@link PublicationStatus})는 독립된
 * 두 상태축이다. 생성 완료가 곧 공개를 뜻하지 않으며 확정과 supersede는 별도로 전이한다.
 */
@Entity
@Table(name = "reports", uniqueConstraints = @UniqueConstraint(
        name = "uk_reports_group_type_period_language_revision",
        columnNames = {"group_id", "type", "period_start", "period_end", "language", "revision"}))
public class WeeklyReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_member_id")
    private GroupMember requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false, length = 5)
    private String language;

    @Column(nullable = false)
    private int revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false, length = 20)
    private PublicationStatus publicationStatus;

    @Column(name = "editor_version", nullable = false)
    private long editorVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_report_id")
    private WeeklyReport sourceReport;

    @JdbcTypeCode(Types.LONGVARCHAR)
    @Column(name = "metrics_json", nullable = false)
    private String metricsJson;

    @JdbcTypeCode(Types.LONGVARCHAR)
    @Column(name = "ai_context_json")
    private String aiContextJson;

    @JdbcTypeCode(Types.LONGVARCHAR)
    @Column(name = "reference_index_json")
    private String referenceIndexJson;

    @JdbcTypeCode(Types.LONGVARCHAR)
    @Column(name = "evidence_json")
    private String evidenceJson;

    @JdbcTypeCode(Types.LONGVARCHAR)
    @Column(name = "ai_summary_json")
    private String aiSummaryJson;

    @JdbcTypeCode(Types.LONGVARCHAR)
    @Column(name = "editorial_json")
    private String editorialJson;

    @Column(length = 80)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 30)
    private String promptVersion;

    @Column(name = "schema_version", nullable = false, length = 30)
    private String schemaVersion;

    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "generation_started_at")
    private Instant generationStartedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    private LocalDateTime generatedAt;

    private LocalDateTime finalizedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finalized_by_member_id")
    private GroupMember finalizedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected WeeklyReport() {}

    public WeeklyReport(Group group, GroupMember requestedBy, LocalDate periodStart, LocalDate periodEnd,
            String language, int revision, TriggerType triggerType, String metricsJson,
            String promptVersion, String schemaVersion, LocalDateTime now) {
        this(group, requestedBy, periodStart, periodEnd, language, revision, triggerType,
                metricsJson, null, null, null, null, promptVersion, schemaVersion, now);
    }

    public WeeklyReport(Group group, GroupMember requestedBy, LocalDate periodStart,
            LocalDate periodEnd, String language, int revision, TriggerType triggerType,
            String metricsJson, String aiContextJson, String referenceIndexJson,
            String evidenceJson, WeeklyReport sourceReport,
            String promptVersion, String schemaVersion, LocalDateTime now) {
        this.group = group;
        this.requestedBy = requestedBy;
        this.type = Type.WEEKLY_AI;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.language = language;
        this.revision = revision;
        this.triggerType = triggerType;
        this.metricsJson = metricsJson;
        this.aiContextJson = aiContextJson;
        this.referenceIndexJson = referenceIndexJson;
        this.evidenceJson = evidenceJson;
        this.sourceReport = sourceReport;
        this.promptVersion = promptVersion;
        this.schemaVersion = schemaVersion;
        this.status = Status.PENDING;
        this.publicationStatus = ("v3".equals(schemaVersion) || "v4".equals(schemaVersion))
                ? PublicationStatus.DRAFT : PublicationStatus.LEGACY;
        this.editorVersion = 0;
        this.attemptCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void start(String metricsJson, String promptVersion, String schemaVersion,
            Instant startedAt, LocalDateTime now) {
        start(metricsJson, aiContextJson, referenceIndexJson, evidenceJson,
                promptVersion, schemaVersion, startedAt, now);
    }

    public void start(String metricsJson, String aiContextJson, String referenceIndexJson,
            String evidenceJson, String promptVersion, String schemaVersion,
            Instant startedAt, LocalDateTime now) {
        this.metricsJson = metricsJson;
        this.aiContextJson = aiContextJson;
        this.referenceIndexJson = referenceIndexJson;
        this.evidenceJson = evidenceJson;
        this.aiSummaryJson = null;
        this.editorialJson = null;
        this.model = null;
        this.inputTokens = null;
        this.outputTokens = null;
        this.totalTokens = null;
        this.failureCode = null;
        this.generatedAt = null;
        this.promptVersion = promptVersion;
        this.schemaVersion = schemaVersion;
        this.status = Status.GENERATING;
        this.generationStartedAt = startedAt;
        this.attemptCount++;
        this.updatedAt = now;
    }

    public void complete(String aiSummaryJson, String model, int inputTokens, int outputTokens,
            int totalTokens, LocalDateTime now) {
        this.aiSummaryJson = aiSummaryJson;
        this.editorialJson = aiSummaryJson;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.failureCode = null;
        this.status = Status.COMPLETED;
        this.generatedAt = now;
        this.generationStartedAt = null;
        this.updatedAt = now;
    }

    public void editDraft(String editorialJson, long expectedEditorVersion, LocalDateTime now) {
        requireDraft(expectedEditorVersion);
        this.editorialJson = editorialJson;
        this.editorVersion++;
        this.updatedAt = now;
    }

    public void finalizeReport(GroupMember leader, long expectedEditorVersion, LocalDateTime now) {
        requireDraft(expectedEditorVersion);
        this.publicationStatus = PublicationStatus.FINALIZED;
        this.finalizedBy = leader;
        this.finalizedAt = now;
        this.updatedAt = now;
    }

    public void supersede(LocalDateTime now) {
        if (publicationStatus != PublicationStatus.LEGACY) {
            this.publicationStatus = PublicationStatus.SUPERSEDED;
            this.updatedAt = now;
        }
    }

    private void requireDraft(long expectedEditorVersion) {
        if (status != Status.COMPLETED || publicationStatus != PublicationStatus.DRAFT) {
            throw new IllegalStateException("Report is not an editable draft");
        }
        if (editorVersion != expectedEditorVersion) {
            throw new IllegalStateException("Report editor version conflict");
        }
    }

    public void fail(String failureCode, String model, Integer inputTokens, Integer outputTokens,
            Integer totalTokens, LocalDateTime now) {
        this.failureCode = failureCode;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.status = Status.FAILED;
        this.generationStartedAt = null;
        this.updatedAt = now;
    }

    public boolean hasExpiredLease(Instant now, Duration lease) {
        return status == Status.GENERATING && generationStartedAt != null
                && !generationStartedAt.plus(lease).isAfter(now);
    }

    public boolean ownsGeneratingAttempt(int attempt) {
        return status == Status.GENERATING && attemptCount == attempt;
    }

    public Long getId() { return id; }
    public Group getGroup() { return group; }
    public GroupMember getRequestedBy() { return requestedBy; }
    public Type getType() { return type; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public String getLanguage() { return language; }
    public int getRevision() { return revision; }
    public TriggerType getTriggerType() { return triggerType; }
    public Status getStatus() { return status; }
    public PublicationStatus getPublicationStatus() { return publicationStatus; }
    public long getEditorVersion() { return editorVersion; }
    public WeeklyReport getSourceReport() { return sourceReport; }
    public String getMetricsJson() { return metricsJson; }
    public String getAiContextJson() { return aiContextJson; }
    public String getReferenceIndexJson() { return referenceIndexJson; }
    public String getEvidenceJson() { return evidenceJson; }
    public String getAiSummaryJson() { return aiSummaryJson; }
    public String getEditorialJson() { return editorialJson; }
    public String getModel() { return model; }
    public String getPromptVersion() { return promptVersion; }
    public String getSchemaVersion() { return schemaVersion; }
    public Integer getInputTokens() { return inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public String getFailureCode() { return failureCode; }
    public Instant getGenerationStartedAt() { return generationStartedAt; }
    public int getAttemptCount() { return attemptCount; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public LocalDateTime getFinalizedAt() { return finalizedAt; }
    public GroupMember getFinalizedBy() { return finalizedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public enum Type { WEEKLY_AI }
    public enum TriggerType { USER, SCHEDULED }
    public enum Status { PENDING, GENERATING, COMPLETED, FAILED }
    public enum PublicationStatus { LEGACY, DRAFT, FINALIZED, SUPERSEDED }
}
