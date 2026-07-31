package com.teamproject.report.infrastructure.openai.contract;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;
import java.util.Optional;

/**
 * OpenAI Structured Outputs 생성 전용 계약 클래스 (M7).
 * JSON Schema 식별자: {@code ai-weekly-report-analysis.v1}
 */
@JsonClassDescription("Validated AI analysis for the WorkTaskFlow v7-2 weekly report")
public final class AiWeeklyReportAnalysisContract {

    public String schemaVersion;
    public AnalysisStatus analysisStatus;
    public ExecutiveJudgment executiveJudgment;
    public Achievement achievement;
    public List<Issue> issues;
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

    public static final class ExecutiveJudgment {
        public String headline;
        public String interpretation;
        public List<String> metricRefs;
        public List<String> evidenceTaskRefs;
        public Confidence confidence;
        public List<String> missingEvidence;
    }

    public static final class Achievement {
        public AchievementStatus status;
        public String headline;
        public String summary;
        public List<String> evidenceTaskRefs;
    }

    public static final class Issue {
        public Priority priority;
        public String candidateRef;
        public String severity;
        public String title;
        public String impact;
        public Confidence confidence;
        public List<String> taskRefs;
        public List<String> evidenceCodes;
        public List<String> missingEvidence;
        public String integratedJudgment;
        public String requiredDecision;
        public Decision decision;
    }

    public static final class Decision {
        public String title;
        public String question;
        public String recommendedOptionCode;
        public String recommendation;
        public String decisionMakerRole;
        public String actionOwnerRole;
        public Deadline deadline;
        public List<String> executionStepCodes;
        public List<String> completionSignalCodes;
    }

    public static final class Deadline {
        @JsonPropertyDescription("MEETING_END, TASK_DUE, CALENDAR_EVENT, or LEADER_DECISION_REQUIRED")
        public String source;
        public Optional<String> referenceRef;
    }
}
