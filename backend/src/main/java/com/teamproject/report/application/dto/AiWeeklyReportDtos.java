package com.teamproject.report.application.dto;

import java.util.List;

/**
 * v7-2 AI 주간 리포트의 OpenAI 입력 계약. 필드 이름과 enum 값의 정본은
 * {@code docs/contracts/ai-weekly-report-snapshot-v1.schema.json}이며, 이 파일은 그 Schema를
 * Java 타입으로 옮긴 런타임 표현이다. 둘이 어긋나면 이 파일을 고친다.
 *
 * <p>record component 이름이 곧 직렬화 key이므로 이름을 임의로 바꾸면 계약이 깨진다.
 * {@code AiWeeklyReportSchemaFixtureTest}가 Schema를 기계 검증한다.
 *
 * <p>개인정보 경계: 이 계약에는 실명·업무 제목 원문·댓글 원문·설명 원문을 담을 자리가 없다.
 * 표시용 문자열은 {@code safeLabel}뿐이고 그 값은 서버가 신호에서 조합한 비식별 라벨이다.
 */
public final class AiWeeklyReportDtos {
    public static final String SNAPSHOT_SCHEMA_VERSION = "ai-weekly-report-snapshot.v1";

    private AiWeeklyReportDtos() {}

    public record AiWeeklyReportSnapshotV1(
            String schemaVersion,
            ReportContext reportContext,
            SnapshotMetrics metrics,
            SnapshotComparison comparison,
            SnapshotWorkflow workflow,
            List<SnapshotMember> members,
            List<SnapshotTask> tasks,
            List<CalendarConstraint> calendarConstraints,
            List<RiskCandidate> riskCandidates) {}

    public record ReportContext(
            String groupRef,
            SnapshotPeriod period,
            String generatedAt,
            Language language,
            String promptVersion) {}

    /** 기간은 {@code [from, toExclusive)}이며 그룹 timezone 기준 월요일 시작 7일이다. */
    public record SnapshotPeriod(String from, String toExclusive, String timezone) {}

    public enum Language { KO, EN }

    public record SnapshotMetrics(
            int periodTaskCount,
            Integer completionRatePercent,
            Integer onTimeRatePercent,
            int delayedCount,
            Integer averageCompletionHours) {}

    public enum ComparisonStatus { AVAILABLE, NO_BASELINE }

    /** {@code NO_BASELINE}이면 나머지 필드는 모두 null이며 AI는 증감 표현을 쓸 수 없다. */
    public record SnapshotComparison(
            ComparisonStatus status,
            String previousFrom,
            String previousToExclusive,
            Integer periodTaskCountDelta,
            Integer completionRatePointDelta,
            Integer onTimeRatePointDelta,
            Integer delayedCountDelta) {

        public static SnapshotComparison noBaseline() {
            return new SnapshotComparison(
                    ComparisonStatus.NO_BASELINE, null, null, null, null, null, null);
        }
    }

    public record SnapshotWorkflow(
            int requested,
            int acceptedUnassigned,
            int assignedNotStarted,
            int inProgress,
            int onHold,
            int completed) {}

    public record SnapshotMember(
            String memberRef,
            String role,
            int assignedCount,
            int activeCount,
            int completedCount,
            int delayedCount,
            Integer onTimeRatePercent,
            int upcomingCalendarCount) {}

    public enum TaskStatus {
        REQUESTED, TODO, IN_PROGRESS, ON_HOLD, COMPLETED, REJECTED, CANCELLED
    }

    public enum DueState {
        NO_DUE, UPCOMING, DUE_SOON, OVERDUE, COMPLETED_ON_TIME, COMPLETED_LATE
    }

    /** 자유 입력 보류 사유 원문은 전송하지 않는다. 구조화 category만 보낸다. */
    public enum HoldReasonCategory {
        NONE, EXTERNAL_FEEDBACK, DEPENDENCY, RESOURCE_SHORTAGE, PRIORITY_CHANGE, OTHER, UNKNOWN
    }

    public enum SignalCode {
        APPROVED_UNASSIGNED, REQUESTED_PENDING, OVERDUE, DUE_SOON, ON_HOLD,
        CHECKLIST_NOT_STARTED, CHECKLIST_STALLED, RESOURCE_MISSING, UNRESOLVED_MENTION,
        WORKLOAD_CONCENTRATION, NO_EFFORT_ESTIMATE, NO_COMPLETION_CRITERIA,
        CALENDAR_CONFLICT, COMPLETED, ON_TIME_COMPLETED
    }

    public enum DecisionOptionCode {
        ASSIGN_OWNER_AND_SET_DUE, DEFINE_HOLD_EXIT_CRITERIA, DEFER_SCOPE, APPROVE_SCOPE,
        REQUEST_MORE_EVIDENCE, REBALANCE_WORK, KEEP_CURRENT_PLAN
    }

    public enum ExecutionStepCode {
        ASSIGN_OWNER, SET_DUE, START_CHECKLIST, LINK_RESOURCE, RESOLVE_MENTION,
        SET_HOLD_EXIT_CRITERIA, RESUME_TASK, RECORD_SCOPE_DECISION, SET_NEXT_REVIEW_DATE,
        REBALANCE_ASSIGNEE
    }

    public enum CompletionSignalCode {
        ASSIGNEE_SET, DUE_AT_SET, CHECKLIST_STARTED, RESOURCE_LINKED, MENTION_RESOLVED,
        HOLD_STATE_RECORDED, TASK_RESUMED, SCOPE_DECISION_RECORDED, NEXT_REVIEW_DATE_SET
    }

    public enum Severity { HIGH, MEDIUM, LOW }

    public record SnapshotTask(
            String taskRef,
            String safeLabel,
            TaskStatus status,
            String priority,
            String assigneeRef,
            String createdAt,
            String dueAt,
            String completedAt,
            DueState dueState,
            TaskChecklist checklist,
            TaskCollaboration collaboration,
            TaskHistory history,
            List<String> calendarEventRefs,
            List<SignalCode> signalCodes,
            List<DecisionOptionCode> allowedDecisionOptionCodes,
            List<ExecutionStepCode> allowedExecutionStepCodes,
            List<CompletionSignalCode> allowedCompletionSignalCodes) {}

    public record TaskChecklist(int completed, int total) {}

    /** 원문이 아니라 집계 수치만 담는다. */
    public record TaskCollaboration(
            int commentCount, int unresolvedMentionCount, int resourceLinkCount) {}

    public record TaskHistory(
            String lastTransitionCode,
            HoldReasonCategory holdReasonCategory,
            int reopenedCount) {}

    public record CalendarConstraint(
            String eventRef,
            String type,
            String safeLabel,
            String startAt,
            String endAt,
            List<String> relatedTaskRefs) {}

    /** 서버가 확정한 위험 후보. 생성은 policy engine(M3)이 담당한다. */
    public record RiskCandidate(
            String candidateRef,
            String riskCode,
            Severity severity,
            List<String> taskRefs,
            List<SignalCode> evidenceCodes,
            List<DecisionOptionCode> allowedOptionCodes,
            List<ExecutionStepCode> allowedExecutionStepCodes,
            List<CompletionSignalCode> allowedCompletionSignalCodes) {}
}
