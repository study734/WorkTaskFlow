package com.teamproject.report.application.dto;

import com.teamproject.report.application.dto.AiWeeklyReportDtos.*;

import java.util.List;

/**
 * v7-2 AI 주간 리포트의 OpenAI 분석 및 Fallback 분석 계약 DTO.
 * 정본 Schema: {@code docs/contracts/ai-weekly-report-analysis-v1.schema.json}
 */
public final class AiWeeklyReportAnalysisDtos {
    public static final String ANALYSIS_SCHEMA_VERSION = "ai-weekly-report-analysis.v1";

    private AiWeeklyReportAnalysisDtos() {}

    public enum AnalysisStatus {
        NORMAL, PARTIAL, NO_ACTION_REQUIRED
    }

    public enum AchievementStatus {
        AVAILABLE, NONE
    }

    public enum MetricRef {
        PERIOD_TASK_COUNT,
        COMPLETION_RATE,
        ON_TIME_RATE,
        DELAYED_COUNT,
        PERIOD_TASK_COUNT_DELTA,
        COMPLETION_RATE_DELTA,
        ON_TIME_RATE_DELTA,
        DELAYED_COUNT_DELTA;

        public boolean isDelta() {
            return this == PERIOD_TASK_COUNT_DELTA
                    || this == COMPLETION_RATE_DELTA
                    || this == ON_TIME_RATE_DELTA
                    || this == DELAYED_COUNT_DELTA;
        }
    }

    public enum Confidence {
        HIGH, MEDIUM, INSUFFICIENT_EVIDENCE
    }

    public enum IssuePriority {
        P1, P2, P3
    }

    public record ExecutiveJudgment(
            String headline,
            String interpretation,
            List<MetricRef> metricRefs,
            List<String> evidenceTaskRefs,
            Confidence confidence,
            List<String> missingEvidence) {}

    public record Achievement(
            AchievementStatus status,
            String headline,
            String summary,
            List<String> evidenceTaskRefs) {

        public static Achievement none() {
            return new Achievement(AchievementStatus.NONE, "", "", List.of());
        }
    }

    public record IssueDeadline(
            String source,
            String referenceRef) {}

    public record IssueDecision(
            String title,
            String question,
            DecisionOptionCode recommendedOptionCode,
            String recommendation,
            String decisionMakerRole,
            String actionOwnerRole,
            IssueDeadline deadline,
            List<ExecutionStepCode> executionStepCodes,
            List<CompletionSignalCode> completionSignalCodes) {}

    public record AnalysisIssue(
            IssuePriority priority,
            String candidateRef,
            Severity severity,
            String title,
            String impact,
            Confidence confidence,
            List<String> taskRefs,
            List<SignalCode> evidenceCodes,
            List<String> missingEvidence,
            String integratedJudgment,
            String requiredDecision,
            IssueDecision decision) {}

    public record AiWeeklyReportAnalysisV1(
            String schemaVersion,
            AnalysisStatus analysisStatus,
            ExecutiveJudgment executiveJudgment,
            Achievement achievement,
            List<AnalysisIssue> issues,
            List<String> globalMissingEvidence) {}
}
