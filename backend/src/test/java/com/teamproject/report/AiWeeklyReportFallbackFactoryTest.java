package com.teamproject.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
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
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiWeeklyReportFallbackFactoryTest {

    private static final String ANALYSIS_SCHEMA = "/ai/ai-weekly-report-analysis-v1.schema.json";
    private static final String SNAPSHOT_EXAMPLE = "/ai/ai-weekly-report-snapshot-v1.example.json";

    private final ObjectMapper json = new ObjectMapper();
    private final AiWeeklyReportPolicyEngine policyEngine = new AiWeeklyReportPolicyEngine();
    private final AiWeeklyReportFallbackFactory fallbackFactory = new AiWeeklyReportFallbackFactory();
    private final AiWeeklyReportAnalysisValidator validator = new AiWeeklyReportAnalysisValidator();

    private AiWeeklyReportSnapshotV1 snapshot;

    @BeforeEach
    void setUp() throws IOException {
        InputStream stream = getClass().getResourceAsStream(SNAPSHOT_EXAMPLE);
        AiWeeklyReportSnapshotV1 raw = json.readValue(stream, AiWeeklyReportSnapshotV1.class);
        snapshot = policyEngine.evaluate(raw);
    }

    @Test
    @DisplayName("생성된 Fallback 분석이 JSON Schema와 Business Validator를 모두 통과한다")
    void createsValidFallbackSatisfyingSchemaAndValidator() {
        AiWeeklyReportAnalysisV1 fallback = fallbackFactory.create(snapshot);

        // 1. Validator test
        ValidationResult validationResult = validator.validate(snapshot, fallback);
        assertThat(validationResult.valid()).isTrue();

        // 2. Schema test
        JsonNode jsonNode = json.valueToTree(fallback);
        Set<ValidationMessage> schemaErrors = validateSchema(ANALYSIS_SCHEMA, jsonNode);
        assertThat(schemaErrors).isEmpty();
    }

    @Test
    @DisplayName("위험 후보가 없으면 NO_ACTION_REQUIRED 상태와 빈 이슈 목록을 반환한다")
    void noActionRequiredWhenNoCandidates() {
        AiWeeklyReportSnapshotV1 emptyRiskSnapshot = new AiWeeklyReportSnapshotV1(
                snapshot.schemaVersion(),
                snapshot.reportContext(),
                snapshot.metrics(),
                snapshot.comparison(),
                snapshot.workflow(),
                snapshot.members(),
                List.of(),
                snapshot.calendarConstraints(),
                List.of()
        );

        AiWeeklyReportAnalysisV1 fallback = fallbackFactory.create(emptyRiskSnapshot);

        assertThat(fallback.analysisStatus()).isEqualTo(AnalysisStatus.NO_ACTION_REQUIRED);
        assertThat(fallback.issues()).isEmpty();

        ValidationResult validationResult = validator.validate(emptyRiskSnapshot, fallback);
        assertThat(validationResult.valid()).isTrue();
    }

    @Test
    @DisplayName("완료 업무가 없는 경우 achievement status는 NONE이며 빈 필드를 가진다")
    void achievementStatusNoneWhenNoCompletedTask() {
        List<SnapshotTask> nonCompletedTasks = snapshot.tasks().stream()
                .filter(t -> t.status() != TaskStatus.COMPLETED)
                .toList();

        AiWeeklyReportSnapshotV1 noCompletedSnapshot = new AiWeeklyReportSnapshotV1(
                snapshot.schemaVersion(),
                snapshot.reportContext(),
                snapshot.metrics(),
                snapshot.comparison(),
                snapshot.workflow(),
                snapshot.members(),
                nonCompletedTasks,
                snapshot.calendarConstraints(),
                snapshot.riskCandidates()
        );

        AiWeeklyReportAnalysisV1 fallback = fallbackFactory.create(noCompletedSnapshot);

        assertThat(fallback.achievement().status()).isEqualTo(AchievementStatus.NONE);
        assertThat(fallback.achievement().headline()).isEmpty();
        assertThat(fallback.achievement().summary()).isEmpty();
        assertThat(fallback.achievement().evidenceTaskRefs()).isEmpty();

        ValidationResult validationResult = validator.validate(noCompletedSnapshot, fallback);
        assertThat(validationResult.valid()).isTrue();
    }

    /**
     * 유료 서비스 화면에 나가는 문장이다. 위험 후보가 없다는 이유로 일반 문구 두 줄만
     * 남으면 그 주에 무슨 일이 있었는지 화면에서 전혀 읽을 수 없다.
     */
    @Test
    @DisplayName("위험 후보가 없어도 Snapshot의 KPI와 workflow 수치를 문장으로 전달한다")
    void reportsKpiAndWorkflowEvenWithoutRiskCandidates() {
        AiWeeklyReportSnapshotV1 noRisk = withRiskCandidates(List.of());

        AiWeeklyReportAnalysisV1 fallback = fallbackFactory.create(noRisk);
        String headline = fallback.executiveJudgment().headline();
        String interpretation = fallback.executiveJudgment().interpretation();
        SnapshotWorkflow workflow = noRisk.workflow();
        SnapshotMetrics metrics = noRisk.metrics();

        assertThat(headline)
                .contains(String.valueOf(metrics.periodTaskCount()))
                .contains(String.valueOf(workflow.completed()))
                .contains(String.valueOf(workflow.inProgress()))
                .contains(String.valueOf(workflow.onHold()));
        assertThat(interpretation).contains(String.valueOf(metrics.completionRatePercent()));
        assertThat(interpretation).contains("위험 후보는 선정되지 않았습니다");
        assertThat(headline.length()).isLessThanOrEqualTo(160);
        assertThat(interpretation.length()).isLessThanOrEqualTo(360);

        assertThat(validator.validate(noRisk, fallback).valid()).isTrue();
        assertThat(validateSchema(ANALYSIS_SCHEMA, json.valueToTree(fallback))).isEmpty();
    }

    @Test
    @DisplayName("지연·승인 대기 업무 건수를 우선 확인 대상으로 적는다")
    void namesDelayedAndPendingApprovalCounts() {
        AiWeeklyReportAnalysisV1 fallback = fallbackFactory.create(snapshot);
        String interpretation = fallback.executiveJudgment().interpretation();

        if (snapshot.metrics().delayedCount() > 0) {
            assertThat(interpretation)
                    .contains("지연 업무 " + snapshot.metrics().delayedCount() + "건");
        }
        if (snapshot.workflow().requested() > 0) {
            assertThat(interpretation)
                    .contains("승인 대기 업무 " + snapshot.workflow().requested() + "건");
        }
        assertThat(interpretation).contains("우선 확인해야 합니다");
    }

    @Test
    @DisplayName("업무가 하나도 없을 때만 데이터 없음 문구를 쓴다")
    void usesTheEmptyPeriodWordingOnlyWithoutTasks() {
        AiWeeklyReportSnapshotV1 empty = new AiWeeklyReportSnapshotV1(
                snapshot.schemaVersion(), snapshot.reportContext(),
                new SnapshotMetrics(0, null, null, 0, null),
                SnapshotComparison.noBaseline(),
                new SnapshotWorkflow(0, 0, 0, 0, 0, 0),
                List.of(), List.of(), List.of(), List.of());

        AiWeeklyReportAnalysisV1 fallback = fallbackFactory.create(empty);

        assertThat(fallback.executiveJudgment().headline())
                .isEqualTo("이번 주 기간에 집계된 확정 업무가 없습니다.");
        assertThat(fallback.achievement().status()).isEqualTo(AchievementStatus.NONE);
        assertThat(validator.validate(empty, fallback).valid()).isTrue();
        assertThat(validateSchema(ANALYSIS_SCHEMA, json.valueToTree(fallback))).isEmpty();
    }

    @Test
    @DisplayName("완료 업무가 있으면 achievement를 실제 완료 업무 ref로 남긴다")
    void keepsAchievementWhenACompletedTaskExists() {
        AiWeeklyReportAnalysisV1 fallback = fallbackFactory.create(snapshot);

        SnapshotTask completed = snapshot.tasks().stream()
                .filter(task -> task.status() == TaskStatus.COMPLETED)
                .findFirst().orElseThrow();
        assertThat(fallback.achievement().status()).isEqualTo(AchievementStatus.AVAILABLE);
        assertThat(fallback.achievement().evidenceTaskRefs())
                .containsExactly(completed.taskRef());
    }

    private AiWeeklyReportSnapshotV1 withRiskCandidates(List<RiskCandidate> candidates) {
        return new AiWeeklyReportSnapshotV1(
                snapshot.schemaVersion(), snapshot.reportContext(), snapshot.metrics(),
                snapshot.comparison(), snapshot.workflow(), snapshot.members(),
                snapshot.tasks(), snapshot.calendarConstraints(), candidates);
    }

    private Set<ValidationMessage> validateSchema(String schemaResource, JsonNode document) {
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .formatAssertionsEnabled(true)
                .build();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream schema = getClass().getResourceAsStream(schemaResource)) {
            JsonSchema compiled = factory.getSchema(schema, config);
            return compiled.validate(document);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
