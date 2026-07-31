package com.teamproject.report.application;

import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos;
import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.*;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * v7-2 AI 주간 리포트 분석 결과의 서버 비즈니스 유효성 검증기 (M4).
 * JSON Schema 검증 이후 업무 존재 여부, 허용 조치 코드 부분집합, 우선순위 연속성,
 * 비교 baseline 충돌, confidence-missingEvidence 연관 관계 등을 검사한다.
 */
@Component
public class AiWeeklyReportAnalysisValidator {

    /** docs/contracts/ai-weekly-report-analysis-v1.schema.json의 역할 enum과 같은 목록이다. */
    private static final Set<String> DECISION_MAKER_ROLES = Set.of("LEADER", "GROUP_ADMIN");
    private static final Set<String> ACTION_OWNER_ROLES =
            Set.of("SELECTED_MEMBER", "CURRENT_ASSIGNEE", "REQUESTER", "LEADER", "TEAM");

    public record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult fail(List<String> errors) {
            return new ValidationResult(false, List.copyOf(errors));
        }

        public static ValidationResult fail(String singleError) {
            return new ValidationResult(false, List.of(singleError));
        }
    }

    public ValidationResult validate(AiWeeklyReportSnapshotV1 snapshot, AiWeeklyReportAnalysisV1 analysis) {
        if (snapshot == null || analysis == null) {
            return ValidationResult.fail("Snapshot and analysis must not be null");
        }

        List<String> errors = new ArrayList<>();

        // 1. Schema version check
        if (!AiWeeklyReportAnalysisDtos.ANALYSIS_SCHEMA_VERSION.equals(analysis.schemaVersion())) {
            errors.add("Invalid analysis schemaVersion: " + analysis.schemaVersion());
        }

        Set<String> knownTaskRefs = snapshot.tasks().stream()
                .map(SnapshotTask::taskRef)
                .collect(Collectors.toSet());

        Set<String> completedTaskRefs = snapshot.tasks().stream()
                .filter(t -> t.status() == TaskStatus.COMPLETED)
                .map(SnapshotTask::taskRef)
                .collect(Collectors.toSet());

        Map<String, RiskCandidate> candidateMap = snapshot.riskCandidates().stream()
                .collect(Collectors.toMap(RiskCandidate::candidateRef, c -> c, (c1, c2) -> c1));

        // 2. Executive Judgment validation
        ExecutiveJudgment ej = analysis.executiveJudgment();
        if (ej != null) {
            validateConfidence(ej.confidence(), ej.missingEvidence(), "executiveJudgment", errors);

            if (ej.evidenceTaskRefs() != null) {
                for (String tRef : ej.evidenceTaskRefs()) {
                    if (!knownTaskRefs.contains(tRef)) {
                        errors.add("executiveJudgment references unknown taskRef: " + tRef);
                    }
                }
            }

            if (snapshot.comparison() != null && snapshot.comparison().status() == ComparisonStatus.NO_BASELINE) {
                if (ej.metricRefs() != null) {
                    for (MetricRef mRef : ej.metricRefs()) {
                        if (mRef.isDelta()) {
                            errors.add("NO_BASELINE snapshot cannot reference delta metricRef: " + mRef);
                        }
                    }
                }
            }
        } else {
            errors.add("executiveJudgment must not be null");
        }

        // 3. Achievement validation
        Achievement ach = analysis.achievement();
        if (ach == null) {
            errors.add("achievement must not be null");
        } else {
            if (ach.status() == AchievementStatus.NONE) {
                if (ach.headline() != null && !ach.headline().isEmpty()) {
                    errors.add("Achievement headline must be empty when status is NONE");
                }
                if (ach.summary() != null && !ach.summary().isEmpty()) {
                    errors.add("Achievement summary must be empty when status is NONE");
                }
                if (ach.evidenceTaskRefs() != null && !ach.evidenceTaskRefs().isEmpty()) {
                    errors.add("Achievement evidenceTaskRefs must be empty when status is NONE");
                }
            } else if (ach.status() == AchievementStatus.AVAILABLE) {
                if (ach.evidenceTaskRefs() != null) {
                    for (String tRef : ach.evidenceTaskRefs()) {
                        if (!knownTaskRefs.contains(tRef)) {
                            errors.add("Achievement references unknown taskRef: " + tRef);
                        } else if (!completedTaskRefs.contains(tRef)) {
                            errors.add("Achievement evidenceTaskRef must refer to a COMPLETED task: " + tRef);
                        }
                    }
                }
            }
        }

        // 4. Issues validation
        List<AnalysisIssue> issues = analysis.issues();
        if (issues != null) {
            if (issues.size() > 3) {
                errors.add("issues size must be at most 3, got: " + issues.size());
            }

            IssuePriority[] expectedPriorities = IssuePriority.values(); // P1, P2, P3
            for (int i = 0; i < issues.size(); i++) {
                AnalysisIssue issue = issues.get(i);
                if (issue.priority() != expectedPriorities[i]) {
                    errors.add("Issue at index " + i + " must have priority " + expectedPriorities[i] + " but got " + issue.priority());
                }

                validateConfidence(issue.confidence(), issue.missingEvidence(), "issue[" + i + "]", errors);

                RiskCandidate candidate = candidateMap.get(issue.candidateRef());
                if (candidate == null) {
                    errors.add("Issue references unknown candidateRef: " + issue.candidateRef());
                } else {
                    if (issue.taskRefs() != null) {
                        for (String tRef : issue.taskRefs()) {
                            if (!knownTaskRefs.contains(tRef)) {
                                errors.add("Issue references unknown taskRef: " + tRef);
                            }
                        }
                    }

                    if (issue.evidenceCodes() != null) {
                        for (SignalCode code : issue.evidenceCodes()) {
                            if (!candidate.evidenceCodes().contains(code)) {
                                errors.add("Issue evidenceCode " + code + " is not a subset of candidate evidenceCodes");
                            }
                        }
                    }

                    IssueDecision dec = issue.decision();
                    if (dec != null) {
                        // 역할은 Schema에 enum으로 선언돼 있지만 런타임에서 아무도 읽지 않았다.
                        // 실제로 모델이 목록에 없는 MEMBER를 돌려줬고 그대로 문서에 찍혔다.
                        if (dec.decisionMakerRole() != null && !DECISION_MAKER_ROLES.contains(dec.decisionMakerRole())) {
                            errors.add("Decision decisionMakerRole is not allowed: " + dec.decisionMakerRole());
                        }
                        if (dec.actionOwnerRole() != null && !ACTION_OWNER_ROLES.contains(dec.actionOwnerRole())) {
                            errors.add("Decision actionOwnerRole is not allowed: " + dec.actionOwnerRole());
                        }

                        if (dec.recommendedOptionCode() != null && !candidate.allowedOptionCodes().contains(dec.recommendedOptionCode())) {
                            errors.add("Decision recommendedOptionCode " + dec.recommendedOptionCode() + " is not allowed for candidate " + candidate.candidateRef());
                        }

                        if (dec.executionStepCodes() != null) {
                            for (ExecutionStepCode step : dec.executionStepCodes()) {
                                if (!candidate.allowedExecutionStepCodes().contains(step)) {
                                    errors.add("Decision executionStepCode " + step + " is not allowed for candidate " + candidate.candidateRef());
                                }
                            }
                        }

                        if (dec.completionSignalCodes() != null) {
                            for (CompletionSignalCode comp : dec.completionSignalCodes()) {
                                if (!candidate.allowedCompletionSignalCodes().contains(comp)) {
                                    errors.add("Decision completionSignalCode " + comp + " is not allowed for candidate " + candidate.candidateRef());
                                }
                            }
                        }
                    }
                }
            }
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }

    private void validateConfidence(Confidence confidence, List<String> missingEvidence, String context, List<String> errors) {
        if (confidence == Confidence.HIGH) {
            if (missingEvidence != null && !missingEvidence.isEmpty()) {
                errors.add(context + " with HIGH confidence cannot have missingEvidence");
            }
        } else if (confidence == Confidence.INSUFFICIENT_EVIDENCE) {
            if (missingEvidence == null || missingEvidence.isEmpty()) {
                errors.add(context + " with INSUFFICIENT_EVIDENCE must have at least one missingEvidence");
            }
        }
    }
}
