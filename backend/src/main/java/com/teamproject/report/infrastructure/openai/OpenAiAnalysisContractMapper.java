package com.teamproject.report.infrastructure.openai;

import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos;
import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.*;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.*;
import com.teamproject.report.infrastructure.openai.contract.AiWeeklyReportAnalysisContract;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI SDK Structured Output 계약 클래스와 도메인 DTO 간 변환 매퍼 (M7).
 */
@Component
public class OpenAiAnalysisContractMapper {

    public AiWeeklyReportAnalysisV1 toDomain(AiWeeklyReportAnalysisContract contract) {
        if (contract == null) {
            return null;
        }

        AnalysisStatus status = contract.analysisStatus != null
                ? AnalysisStatus.valueOf(contract.analysisStatus.name())
                : AnalysisStatus.NORMAL;

        ExecutiveJudgment ej = null;
        if (contract.executiveJudgment != null) {
            List<MetricRef> mRefs = new ArrayList<>();
            if (contract.executiveJudgment.metricRefs != null) {
                for (var mr : contract.executiveJudgment.metricRefs) {
                    if (mr != null) mRefs.add(MetricRef.valueOf(mr.name()));
                }
            }
            Confidence conf = contract.executiveJudgment.confidence != null
                    ? Confidence.valueOf(contract.executiveJudgment.confidence.name())
                    : Confidence.HIGH;

            ej = new ExecutiveJudgment(
                    contract.executiveJudgment.headline,
                    contract.executiveJudgment.interpretation,
                    mRefs,
                    contract.executiveJudgment.evidenceTaskRefs != null ? contract.executiveJudgment.evidenceTaskRefs : List.of(),
                    conf,
                    contract.executiveJudgment.missingEvidence != null ? contract.executiveJudgment.missingEvidence : List.of()
            );
        }

        Achievement ach;
        if (contract.achievement != null) {
            AchievementStatus aStatus = contract.achievement.status != null
                    ? AchievementStatus.valueOf(contract.achievement.status.name())
                    : AchievementStatus.NONE;
            ach = new Achievement(
                    aStatus,
                    contract.achievement.headline != null ? contract.achievement.headline : "",
                    contract.achievement.summary != null ? contract.achievement.summary : "",
                    contract.achievement.evidenceTaskRefs != null ? contract.achievement.evidenceTaskRefs : List.of()
            );
        } else {
            ach = Achievement.none();
        }

        List<AnalysisIssue> issues = new ArrayList<>();
        if (contract.issues != null) {
            for (AiWeeklyReportAnalysisContract.Issue issue : contract.issues) {
                IssuePriority priority = issue.priority != null ? IssuePriority.valueOf(issue.priority.name()) : IssuePriority.P1;
                Severity severity = issue.severity != null ? Severity.valueOf(issue.severity.name()) : Severity.MEDIUM;
                Confidence conf = issue.confidence != null ? Confidence.valueOf(issue.confidence.name()) : Confidence.HIGH;

                List<SignalCode> evidenceCodes = new ArrayList<>();
                if (issue.evidenceCodes != null) {
                    for (var ec : issue.evidenceCodes) {
                        if (ec != null) evidenceCodes.add(SignalCode.valueOf(ec.name()));
                    }
                }

                IssueDecision decision = null;
                if (issue.decision != null) {
                    DecisionOptionCode recOpt = issue.decision.recommendedOptionCode != null
                            ? DecisionOptionCode.valueOf(issue.decision.recommendedOptionCode.name())
                            : DecisionOptionCode.KEEP_CURRENT_PLAN;

                    IssueDeadline deadline = null;
                    if (issue.decision.deadline != null) {
                        deadline = new IssueDeadline(
                                issue.decision.deadline.source != null ? issue.decision.deadline.source.name() : null,
                                issue.decision.deadline.referenceRef != null ? issue.decision.deadline.referenceRef.orElse(null) : null
                        );
                    }

                    List<ExecutionStepCode> steps = new ArrayList<>();
                    if (issue.decision.executionStepCodes != null) {
                        for (var s : issue.decision.executionStepCodes) {
                            if (s != null) steps.add(ExecutionStepCode.valueOf(s.name()));
                        }
                    }

                    List<CompletionSignalCode> completions = new ArrayList<>();
                    if (issue.decision.completionSignalCodes != null) {
                        for (var c : issue.decision.completionSignalCodes) {
                            if (c != null) completions.add(CompletionSignalCode.valueOf(c.name()));
                        }
                    }

                    decision = new IssueDecision(
                            issue.decision.title,
                            issue.decision.question,
                            recOpt,
                            issue.decision.recommendation,
                            issue.decision.decisionMakerRole != null ? issue.decision.decisionMakerRole.name() : null,
                            issue.decision.actionOwnerRole != null ? issue.decision.actionOwnerRole.name() : null,
                            deadline,
                            steps,
                            completions
                    );
                }

                issues.add(new AnalysisIssue(
                        priority,
                        issue.candidateRef,
                        severity,
                        issue.title,
                        issue.impact,
                        conf,
                        issue.taskRefs != null ? issue.taskRefs : List.of(),
                        evidenceCodes,
                        issue.missingEvidence != null ? issue.missingEvidence : List.of(),
                        issue.integratedJudgment,
                        issue.requiredDecision,
                        decision
                ));
            }
        }

        // schemaVersion은 서버가 소유한 계약 식별자다. 모델은 입력 Snapshot의 값을 그대로
        // 되돌려주기도 해서(`ai-weekly-report-snapshot.v1`) 그대로 믿으면 매번 검증에 걸린다.
        return new AiWeeklyReportAnalysisV1(
                AiWeeklyReportAnalysisDtos.ANALYSIS_SCHEMA_VERSION,
                status,
                ej,
                ach,
                issues,
                contract.globalMissingEvidence != null ? contract.globalMissingEvidence : List.of()
        );
    }
}
