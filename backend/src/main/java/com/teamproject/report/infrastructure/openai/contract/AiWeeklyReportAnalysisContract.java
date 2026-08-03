package com.teamproject.report.infrastructure.openai.contract;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import java.util.List;
import java.util.Optional;

/**
 * OpenAI Structured Outputs 생성 전용 계약 클래스 (M7).
 * JSON Schema 식별자: {@code ai-weekly-report-analysis.v1}
 *
 * <p>닫힌 코드 집합은 전부 enum으로 선언한다. String으로 두면 SDK가 만드는 스키마가 자유
 * 문자열이 되어 모델이 계약 밖 값을 돌려줄 수 있고, 그러면 유료 호출이 끝난 뒤에 매핑이나
 * 검증에서 버려진다. enum이면 응답 단계에서 값이 강제된다.
 */
@JsonClassDescription("Validated AI analysis for the WorkTaskFlow v7-2 weekly report")
public final class AiWeeklyReportAnalysisContract {

    public String schemaVersion;
    public AnalysisStatus analysisStatus;
    public ExecutiveJudgment executiveJudgment;
    public Achievement achievement;
    @ArraySchema(maxItems = 3)
    public List<Issue> issues;
    @ArraySchema(maxItems = 8)
    public List<String> globalMissingEvidence;

    public enum AnalysisStatus {
        NORMAL,
        PARTIAL,
        NO_ACTION_REQUIRED
    }

    public enum AchievementStatus {
        AVAILABLE,
        NONE
    }

    public enum Confidence {
        HIGH,
        MEDIUM,
        INSUFFICIENT_EVIDENCE
    }

    public enum Priority {
        P1,
        P2,
        P3
    }

    public enum Severity {
        HIGH,
        MEDIUM,
        LOW
    }

    public enum MetricRef {
        PERIOD_TASK_COUNT, COMPLETION_RATE, ON_TIME_RATE, DELAYED_COUNT,
        PERIOD_TASK_COUNT_DELTA, COMPLETION_RATE_DELTA, ON_TIME_RATE_DELTA, DELAYED_COUNT_DELTA
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

    public enum DecisionMakerRole {
        LEADER,
        GROUP_ADMIN
    }

    public enum ActionOwnerRole {
        SELECTED_MEMBER, CURRENT_ASSIGNEE, REQUESTER, LEADER, TEAM
    }

    public enum DeadlineSource {
        MEETING_END, TASK_DUE, CALENDAR_EVENT, LEADER_DECISION_REQUIRED
    }

    public static final class ExecutiveJudgment {
        public String headline;
        public String interpretation;
        @ArraySchema(maxItems = 4)
        public List<MetricRef> metricRefs;
        @ArraySchema(maxItems = 5)
        public List<String> evidenceTaskRefs;
        public Confidence confidence;
        @ArraySchema(maxItems = 5)
        public List<String> missingEvidence;
    }

    public static final class Achievement {
        public AchievementStatus status;
        public String headline;
        public String summary;
        @ArraySchema(maxItems = 5)
        public List<String> evidenceTaskRefs;
    }

    public static final class Issue {
        public Priority priority;
        public String candidateRef;
        public Severity severity;
        public String title;
        public String impact;
        public Confidence confidence;
        @ArraySchema(minItems = 1, maxItems = 5)
        public List<String> taskRefs;
        @ArraySchema(minItems = 1, maxItems = 8)
        public List<SignalCode> evidenceCodes;
        @ArraySchema(maxItems = 5)
        public List<String> missingEvidence;
        public String integratedJudgment;
        public String requiredDecision;
        public Decision decision;
    }

    public static final class Decision {
        public String title;
        public String question;
        public DecisionOptionCode recommendedOptionCode;
        public String recommendation;
        public DecisionMakerRole decisionMakerRole;
        public ActionOwnerRole actionOwnerRole;
        public Deadline deadline;
        @ArraySchema(minItems = 1, maxItems = 6)
        public List<ExecutionStepCode> executionStepCodes;
        @ArraySchema(minItems = 1, maxItems = 6)
        public List<CompletionSignalCode> completionSignalCodes;
    }

    public static final class Deadline {
        public DeadlineSource source;
        public Optional<String> referenceRef;
    }
}
