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
    /** 명세 §7.6의 허용 source. */
    private static final Set<String> DEADLINE_SOURCES =
            Set.of("MEETING_END", "TASK_DUE", "CALENDAR_EVENT", "LEADER_DECISION_REQUIRED");

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

        Map<String, SnapshotTask> taskByRef = snapshot.tasks().stream()
                .collect(Collectors.toMap(SnapshotTask::taskRef, t -> t, (a, b) -> a));

        Set<String> knownEventRefs = snapshot.calendarConstraints() == null ? Set.of()
                : snapshot.calendarConstraints().stream()
                        .map(CalendarConstraint::eventRef).collect(Collectors.toSet());

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
                    validateRiskState(candidate, issue, taskByRef, i, errors);

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

                        validateDeadline(dec, knownTaskRefs, knownEventRefs, i, errors);

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

    /**
     * 명세 §7.6. deadline은 정해진 source 넷과 ref 하나로만 이루어진다.
     * 검사하지 않으면 모델이 지어낸 값이 그대로 저장되고 문서 라벨이 default로 빠진다.
     * 역할 enum에서 실제로 겪은 일이다.
     */
    private void validateDeadline(IssueDecision decision, Set<String> knownTaskRefs,
            Set<String> knownEventRefs, int index, List<String> errors) {
        IssueDeadline deadline = decision.deadline();
        if (deadline == null) return;

        if (deadline.source() == null || !DEADLINE_SOURCES.contains(deadline.source())) {
            errors.add("issue[" + index + "] decision deadline source is not allowed: " + deadline.source());
        }

        String ref = deadline.referenceRef();
        if (ref == null) return;
        // CALENDAR_EVENT는 일정을, TASK_DUE는 업무를 가리킨다. 둘 중 어디에도 없으면 화면에서
        // 날짜를 채울 수 없다.
        if (!knownTaskRefs.contains(ref) && !knownEventRefs.contains(ref)) {
            errors.add("issue[" + index + "] decision deadline references unknown ref: " + ref);
        }
    }

    /**
     * 명세 §7.3. AI가 고른 위험이 확정 데이터와 실제로 맞는지 교차 검증한다.
     *
     * <p>이 검사가 없으면 모델이 엉뚱한 업무를 담당자 미지정이나 지연이라고 주장해도 서버가
     * 잡지 못한다. 실제 응답에 "OVERDUE 근거가 서버 지표 delayedCount 0과 일치하지 않는다"고
     * 모델이 스스로 적은 적이 있다. 그때 걸러졌어야 할 자리다.
     *
     * <p>후보 하나가 업무 여러 건과 신호 여러 개를 묶으므로 모든 업무가 모든 신호를 만족할
     * 필요는 없다(부하 편중 후보에 지연 업무 한 건이 섞이는 식이다). **적어도 한 업무**가
     * 뒷받침하면 통과다. 하나도 없으면 근거 없는 주장이다.
     */
    private void validateRiskState(RiskCandidate candidate, AnalysisIssue issue,
            Map<String, SnapshotTask> taskByRef, int index, List<String> errors) {
        List<SignalCode> signals = candidate.evidenceCodes() == null ? List.of() : candidate.evidenceCodes();
        List<SnapshotTask> tasks = (issue.taskRefs() == null ? List.<String>of() : issue.taskRefs()).stream()
                .map(taskByRef::get).filter(Objects::nonNull).toList();
        if (tasks.isEmpty()) return;

        requireAny(signals, SignalCode.APPROVED_UNASSIGNED, tasks, t -> t.assigneeRef() == null,
                index, "unassigned", errors);
        requireAny(signals, SignalCode.OVERDUE, tasks, t -> t.dueState() == DueState.OVERDUE,
                index, "overdue", errors);
        requireAny(signals, SignalCode.ON_HOLD, tasks, t -> t.status() == TaskStatus.ON_HOLD,
                index, "on-hold", errors);
        requireAny(signals, SignalCode.CHECKLIST_NOT_STARTED, tasks,
                t -> t.checklist() != null && t.checklist().completed() == 0,
                index, "untouched checklist", errors);
    }

    private void requireAny(List<SignalCode> signals, SignalCode signal, List<SnapshotTask> tasks,
            java.util.function.Predicate<SnapshotTask> holds, int index, String label, List<String> errors) {
        if (!signals.contains(signal)) return;
        if (tasks.stream().noneMatch(holds)) {
            errors.add("issue[" + index + "] claims a " + label + " risk but no referenced task supports it");
        }
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
