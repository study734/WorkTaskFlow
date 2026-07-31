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
}
