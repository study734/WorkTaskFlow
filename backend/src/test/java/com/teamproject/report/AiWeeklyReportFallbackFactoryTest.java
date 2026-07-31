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
