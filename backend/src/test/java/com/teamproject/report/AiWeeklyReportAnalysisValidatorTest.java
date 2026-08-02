package com.teamproject.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.report.application.AiWeeklyReportAnalysisValidator;
import com.teamproject.report.application.AiWeeklyReportAnalysisValidator.ValidationResult;
import com.teamproject.report.application.AiWeeklyReportFallbackFactory;
import com.teamproject.report.application.AiWeeklyReportPolicyEngine;
import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.*;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiWeeklyReportAnalysisValidatorTest {

    private final ObjectMapper json = new ObjectMapper();
    private final AiWeeklyReportPolicyEngine policyEngine = new AiWeeklyReportPolicyEngine();
    private final AiWeeklyReportFallbackFactory fallbackFactory = new AiWeeklyReportFallbackFactory();
    private final AiWeeklyReportAnalysisValidator validator = new AiWeeklyReportAnalysisValidator();

    private AiWeeklyReportSnapshotV1 snapshot;

    @BeforeEach
    void setUp() throws IOException {
        InputStream stream = getClass().getResourceAsStream("/ai/ai-weekly-report-snapshot-v1.example.json");
        AiWeeklyReportSnapshotV1 raw = json.readValue(stream, AiWeeklyReportSnapshotV1.class);
        snapshot = policyEngine.evaluate(raw);
    }

    @Test
    @DisplayName("정상 생성된 Fallback 결과는 Validator를 통과한다")
    void validFallbackPassesValidator() {
        AiWeeklyReportAnalysisV1 fallback = fallbackFactory.create(snapshot);
        ValidationResult result = validator.validate(snapshot, fallback);

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 candidateRef를 참조하는 경우 거부한다")
    void rejectsUnknownCandidateRef() {
        AiWeeklyReportAnalysisV1 fallback = fallbackFactory.create(snapshot);
        List<AnalysisIssue> badIssues = fallback.issues().stream()
                .map(issue -> new AnalysisIssue(
                        issue.priority(),
                        "UNKNOWN-RISK-REF",
                        issue.severity(),
                        issue.title(),
                        issue.impact(),
                        issue.confidence(),
                        issue.taskRefs(),
                        issue.evidenceCodes(),
                        issue.missingEvidence(),
                        issue.integratedJudgment(),
                        issue.requiredDecision(),
                        issue.decision()
                ))
                .toList();

        AiWeeklyReportAnalysisV1 invalidAnalysis = new AiWeeklyReportAnalysisV1(
                fallback.schemaVersion(),
                fallback.analysisStatus(),
                fallback.executiveJudgment(),
                fallback.achievement(),
                badIssues,
                fallback.globalMissingEvidence()
        );

        ValidationResult result = validator.validate(snapshot, invalidAnalysis);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("unknown candidateRef"));
    }

    @Test
    @DisplayName("HIGH confidence인데 missingEvidence가 존재하면 거부한다")
    void rejectsHighConfidenceWithMissingEvidence() {
        AiWeeklyReportAnalysisV1 fallback = fallbackFactory.create(snapshot);
        ExecutiveJudgment badEj = new ExecutiveJudgment(
                fallback.executiveJudgment().headline(),
                fallback.executiveJudgment().interpretation(),
                fallback.executiveJudgment().metricRefs(),
                fallback.executiveJudgment().evidenceTaskRefs(),
                Confidence.HIGH,
                List.of("MISSING_DATA")
        );

        AiWeeklyReportAnalysisV1 invalidAnalysis = new AiWeeklyReportAnalysisV1(
                fallback.schemaVersion(),
                fallback.analysisStatus(),
                badEj,
                fallback.achievement(),
                fallback.issues(),
                fallback.globalMissingEvidence()
        );

        ValidationResult result = validator.validate(snapshot, invalidAnalysis);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("HIGH confidence cannot have missingEvidence"));
    }

    /**
     * 실제 OpenAI 응답이 계약에 없는 {@code MEMBER}를 actionOwnerRole로 돌려줬는데 그대로 저장되어
     * 사용자 문서에 영문 코드가 찍혔다. Schema에는 enum이 선언돼 있었지만 런타임에서 아무도 읽지 않았다.
     */
    @Test
    @DisplayName("계약에 없는 역할 값을 거부한다")
    void rejectsRolesOutsideTheContract() {
        assertThat(validateWithRoles("LEADER", "MEMBER").errors())
                .anyMatch(e -> e.contains("actionOwnerRole is not allowed: MEMBER"));
        assertThat(validateWithRoles("TEAM_LEADER", "CURRENT_ASSIGNEE").errors())
                .anyMatch(e -> e.contains("decisionMakerRole is not allowed: TEAM_LEADER"));
    }

    @Test
    @DisplayName("계약에 있는 역할 값은 모두 통과한다")
    void acceptsEveryRoleInTheContract() {
        for (String owner : List.of("SELECTED_MEMBER", "CURRENT_ASSIGNEE", "REQUESTER", "LEADER", "TEAM")) {
            assertThat(validateWithRoles("LEADER", owner).valid())
                    .as("actionOwnerRole %s", owner).isTrue();
        }
        for (String maker : List.of("LEADER", "GROUP_ADMIN")) {
            assertThat(validateWithRoles(maker, "CURRENT_ASSIGNEE").valid())
                    .as("decisionMakerRole %s", maker).isTrue();
        }
    }

    /** Fallback 결과의 역할만 바꿔 검증한다. 나머지 필드는 이미 통과가 보장된 값이다. */
    private ValidationResult validateWithRoles(String decisionMakerRole, String actionOwnerRole) {
        AiWeeklyReportAnalysisV1 fallback = fallbackFactory.create(snapshot);
        List<AnalysisIssue> issues = fallback.issues().stream()
                .map(issue -> {
                    IssueDecision d = issue.decision();
                    IssueDecision swapped = new IssueDecision(d.title(), d.question(),
                            d.recommendedOptionCode(), d.recommendation(),
                            decisionMakerRole, actionOwnerRole, d.deadline(),
                            d.executionStepCodes(), d.completionSignalCodes());
                    return new AnalysisIssue(issue.priority(), issue.candidateRef(), issue.severity(),
                            issue.title(), issue.impact(), issue.confidence(), issue.taskRefs(),
                            issue.evidenceCodes(), issue.missingEvidence(), issue.integratedJudgment(),
                            issue.requiredDecision(), swapped);
                })
                .toList();

        return validator.validate(snapshot, new AiWeeklyReportAnalysisV1(
                fallback.schemaVersion(), fallback.analysisStatus(), fallback.executiveJudgment(),
                fallback.achievement(), issues, fallback.globalMissingEvidence()));
    }

    @Test
    @DisplayName("NO_BASELINE snapshot에서 delta metricRef 사용 시 거부한다")
    void rejectsDeltaMetricRefUnderNoBaseline() {
        AiWeeklyReportSnapshotV1 noBaselineSnapshot = new AiWeeklyReportSnapshotV1(
                snapshot.schemaVersion(),
                snapshot.reportContext(),
                snapshot.metrics(),
                SnapshotComparison.noBaseline(),
                snapshot.workflow(),
                snapshot.members(),
                snapshot.tasks(),
                snapshot.calendarConstraints(),
                snapshot.riskCandidates()
        );

        AiWeeklyReportAnalysisV1 fallback = fallbackFactory.create(snapshot);
        ExecutiveJudgment badEj = new ExecutiveJudgment(
                fallback.executiveJudgment().headline(),
                fallback.executiveJudgment().interpretation(),
                List.of(MetricRef.COMPLETION_RATE_DELTA),
                fallback.executiveJudgment().evidenceTaskRefs(),
                Confidence.HIGH,
                List.of()
        );

        AiWeeklyReportAnalysisV1 invalidAnalysis = new AiWeeklyReportAnalysisV1(
                fallback.schemaVersion(),
                fallback.analysisStatus(),
                badEj,
                fallback.achievement(),
                fallback.issues(),
                fallback.globalMissingEvidence()
        );

        ValidationResult result = validator.validate(noBaselineSnapshot, invalidAnalysis);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("NO_BASELINE snapshot cannot reference delta metricRef"));
    }

    /**
     * 명세 §7.6. deadline source는 넷뿐인데 검사하는 곳이 없었다. 역할 enum과 같은 구멍이고,
     * 그쪽에서는 모델이 계약에 없는 MEMBER를 실제로 돌려줬다.
     */
    @Test
    @DisplayName("계약에 없는 deadline source를 거부한다")
    void rejectsAnUnknownDeadlineSource() {
        assertThat(validateWithDeadline(new IssueDeadline("WHENEVER", null)).errors())
                .anyMatch(e -> e.contains("deadline source is not allowed: WHENEVER"));
        for (String source : List.of("MEETING_END", "TASK_DUE", "CALENDAR_EVENT", "LEADER_DECISION_REQUIRED")) {
            assertThat(validateWithDeadline(new IssueDeadline(source, null)).valid())
                    .as("source %s", source).isTrue();
        }
    }

    @Test
    @DisplayName("Snapshot에 없는 ref를 가리키는 deadline을 거부한다")
    void rejectsADeadlineReferenceOutsideTheSnapshot() {
        assertThat(validateWithDeadline(new IssueDeadline("CALENDAR_EVENT", "EVENT-9999")).errors())
                .anyMatch(e -> e.contains("deadline references unknown ref: EVENT-9999"));
    }

    /**
     * 명세 §7.3. AI가 확정 데이터와 맞지 않는 위험을 주장해도 서버가 잡지 못했다. 실제 응답에
     * 모델이 "OVERDUE 근거가 delayedCount 0과 일치하지 않는다"고 스스로 적은 적이 있다.
     */
    @Test
    @DisplayName("근거 업무가 뒷받침하지 않는 위험 주장을 거부한다")
    void rejectsARiskClaimNoTaskSupports() {
        AiWeeklyReportAnalysisV1 fallback = fallbackFactory.create(snapshot);
        AnalysisIssue first = fallback.issues().get(0);
        RiskCandidate candidate = snapshot.riskCandidates().stream()
                .filter(c -> c.candidateRef().equals(first.candidateRef())).findFirst().orElseThrow();
        assertThat(candidate.evidenceCodes()).isNotEmpty();

        // 완료된 업무만 근거로 달면 어떤 위험 신호도 성립하지 않는다.
        String completedRef = snapshot.tasks().stream()
                .filter(t -> t.status() == TaskStatus.COMPLETED)
                .map(SnapshotTask::taskRef).findFirst().orElseThrow();

        AnalysisIssue swapped = new AnalysisIssue(first.priority(), first.candidateRef(),
                first.severity(), first.title(), first.impact(), first.confidence(),
                List.of(completedRef), first.evidenceCodes(), first.missingEvidence(),
                first.integratedJudgment(), first.requiredDecision(), first.decision());

        ValidationResult result = validator.validate(snapshot, new AiWeeklyReportAnalysisV1(
                fallback.schemaVersion(), fallback.analysisStatus(), fallback.executiveJudgment(),
                fallback.achievement(), List.of(swapped), fallback.globalMissingEvidence()));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("no referenced task supports it"));
    }

    private ValidationResult validateWithDeadline(IssueDeadline deadline) {
        AiWeeklyReportAnalysisV1 fallback = fallbackFactory.create(snapshot);
        List<AnalysisIssue> issues = fallback.issues().stream().map(issue -> {
            IssueDecision d = issue.decision();
            IssueDecision swapped = new IssueDecision(d.title(), d.question(), d.recommendedOptionCode(),
                    d.recommendation(), d.decisionMakerRole(), d.actionOwnerRole(), deadline,
                    d.executionStepCodes(), d.completionSignalCodes());
            return new AnalysisIssue(issue.priority(), issue.candidateRef(), issue.severity(),
                    issue.title(), issue.impact(), issue.confidence(), issue.taskRefs(),
                    issue.evidenceCodes(), issue.missingEvidence(), issue.integratedJudgment(),
                    issue.requiredDecision(), swapped);
        }).toList();
        return validator.validate(snapshot, new AiWeeklyReportAnalysisV1(
                fallback.schemaVersion(), fallback.analysisStatus(), fallback.executiveJudgment(),
                fallback.achievement(), issues, fallback.globalMissingEvidence()));
    }
}
