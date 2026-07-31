package com.teamproject.report.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * v7-2 AI 주간 리포트 HTTP API 요청/응답 DTO 정의 (M8).
 */
public class AiWeeklyReportApiDtos {

    public record GenerateReportRequest(
            @NotNull LocalDate from,
            @NotNull LocalDate toExclusive,
            String language,
            boolean regenerate
    ) {}

    public record GenerateReportResponse(
            Long reportId,
            Long groupId,
            LocalDate from,
            LocalDate toExclusive,
            int revision,
            String status,
            String analysisMode,
            LocalDateTime generatedAt,
            String downloadUrl,
            /** 이번 요청으로 새로 만들었는지. false면 저장된 revision을 그대로 돌려준 것이다. */
            boolean createdNew
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AiWeeklyReportView(
            Long reportId,
            Long groupId,
            LocalDate from,
            LocalDate toExclusive,
            int revision,
            String status,
            String analysisMode,
            LocalDateTime generatedAt,
            String downloadUrl,
            ExecutiveJudgmentView executiveJudgment,
            AchievementView achievement,
            List<IssueView> issues,
            List<String> globalMissingEvidence,
            SnapshotMetricsView metrics,
            SnapshotComparisonView comparison,
            SnapshotWorkflowView workflow,
            List<SnapshotTaskView> tasks,
            List<SnapshotMemberView> members,
            List<CalendarConstraintView> calendarConstraints
    ) {}

    public record ExecutiveJudgmentView(
            String headline,
            String interpretation,
            List<String> metricRefs,
            List<String> evidenceTaskRefs,
            List<String> evidenceTaskTitles,
            String confidence,
            List<String> missingEvidence
    ) {}

    public record AchievementView(
            String status,
            String headline,
            String summary,
            List<String> evidenceTaskRefs,
            List<String> evidenceTaskTitles
    ) {}

    public record IssueView(
            String priority,
            String candidateRef,
            String severity,
            String title,
            String realTaskTitle,
            String impact,
            String confidence,
            List<String> taskRefs,
            List<String> taskTitles,
            List<String> evidenceCodes,
            List<String> missingEvidence,
            String integratedJudgment,
            String requiredDecision,
            DecisionView decision
    ) {}

    public record DecisionView(
            String title,
            String question,
            String recommendedOptionCode,
            String recommendation,
            String decisionMakerRole,
            String actionOwnerRole,
            DeadlineView deadline,
            List<String> executionStepCodes,
            List<String> completionSignalCodes
    ) {}

    public record DeadlineView(
            String source,
            String referenceRef,
            String referenceTitle
    ) {}

    public record SnapshotMetricsView(
            long periodTaskCount,
            Integer completionRatePercent,
            Integer onTimeRatePercent,
            int delayedCount,
            Integer averageLeadTimeHours
    ) {}

    public record SnapshotComparisonView(
            String status,
            String previousPeriodFrom,
            String previousPeriodToExclusive,
            Integer taskCountDiff,
            Integer completionRateDiffPercent,
            Integer onTimeRateDiffPercent,
            Integer delayedCountDiff
    ) {}

    public record SnapshotWorkflowView(
            int requestedCount,
            int todoUnassignedCount,
            int todoAssignedCount,
            int inProgressCount,
            int onHoldCount,
            int completedCount
    ) {}

    public record SnapshotTaskView(
            String taskRef,
            String realTitle,
            String safeLabel,
            String status,
            String priority,
            String assigneeRef,
            String assigneeName,
            String createdAt,
            String dueAt,
            String completedAt,
            String dueState,
            TaskChecklistView checklist,
            TaskCollaborationView collaboration,
            TaskHistoryView history,
            List<String> calendarEventRefs
    ) {}

    public record TaskChecklistView(
            int completedCount,
            int totalCount
    ) {}

    public record TaskCollaborationView(
            int commentCount,
            int unresolvedMentionCount,
            int resourceLinkCount
    ) {}

    public record TaskHistoryView(
            String lastTransition,
            String holdReasonCategory,
            int reopenedCount
    ) {}

    public record SnapshotMemberView(
            String memberRef,
            String realName,
            String role,
            int periodTaskCount,
            int activeTaskCount,
            int completedTaskCount,
            int delayedTaskCount,
            Integer onTimeRatePercent,
            int upcomingEventCount
    ) {}

    public record CalendarConstraintView(
            String eventRef,
            String realTitle,
            String eventType,
            String safeLabel,
            String startAt,
            String endAt,
            List<String> relatedTaskRefs
    ) {}
}
