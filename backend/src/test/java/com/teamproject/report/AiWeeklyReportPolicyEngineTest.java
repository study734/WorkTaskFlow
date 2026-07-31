package com.teamproject.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import com.teamproject.report.application.AiWeeklyReportPolicyEngine;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiWeeklyReportPolicyEngineTest {

    private static final String SNAPSHOT_SCHEMA = "/ai/ai-weekly-report-snapshot-v1.schema.json";
    private static final String SNAPSHOT_EXAMPLE = "/ai/ai-weekly-report-snapshot-v1.example.json";

    private final ObjectMapper json = new ObjectMapper();
    private final AiWeeklyReportPolicyEngine policyEngine = new AiWeeklyReportPolicyEngine();

    @Test
    @DisplayName("Fixture snapshot을 평가한 최종 Snapshot이 M0 JSON Schema를 통과한다")
    void evaluatedSnapshotSatisfiesJsonSchema() throws IOException {
        InputStream stream = getClass().getResourceAsStream(SNAPSHOT_EXAMPLE);
        AiWeeklyReportSnapshotV1 initialSnapshot = json.readValue(stream, AiWeeklyReportSnapshotV1.class);

        AiWeeklyReportSnapshotV1 evaluated = policyEngine.evaluate(initialSnapshot);

        JsonNode evaluatedNode = json.valueToTree(evaluated);
        Set<ValidationMessage> validationMessages = validate(SNAPSHOT_SCHEMA, evaluatedNode);
        assertThat(validationMessages).isEmpty();
    }

    @Test
    @DisplayName("위험별 생성 조건 및 금지 조건을 검증한다")
    void riskGenerationAndProhibitionRules() throws IOException {
        InputStream stream = getClass().getResourceAsStream(SNAPSHOT_EXAMPLE);
        AiWeeklyReportSnapshotV1 initialSnapshot = json.readValue(stream, AiWeeklyReportSnapshotV1.class);

        // Modify comparison to NO_BASELINE
        AiWeeklyReportSnapshotV1 noBaselineSnapshot = new AiWeeklyReportSnapshotV1(
                initialSnapshot.schemaVersion(),
                initialSnapshot.reportContext(),
                initialSnapshot.metrics(),
                SnapshotComparison.noBaseline(),
                initialSnapshot.workflow(),
                initialSnapshot.members(),
                initialSnapshot.tasks(),
                initialSnapshot.calendarConstraints(),
                List.of()
        );

        AiWeeklyReportSnapshotV1 evaluated = policyEngine.evaluate(noBaselineSnapshot);

        List<RiskCandidate> candidates = evaluated.riskCandidates();

        // 1. ON_HOLD_LONG and RESOURCE_MISSING must NOT be generated
        assertThat(candidates).noneMatch(c -> c.riskCode().equals("ON_HOLD_LONG"));
        assertThat(candidates).noneMatch(c -> c.riskCode().equals("RESOURCE_MISSING"));

        // 2. UNRESOLVED_MENTION must NOT have HIGH severity
        assertThat(candidates)
                .filteredOn(c -> c.riskCode().equals("UNRESOLVED_MENTION"))
                .allMatch(c -> c.severity() != Severity.HIGH);

        // 3. NO_BASELINE means NO COMPLETION_RATE_DROP or BACKLOG_GROWTH
        assertThat(candidates).noneMatch(c -> c.riskCode().equals("COMPLETION_RATE_DROP"));
        assertThat(candidates).noneMatch(c -> c.riskCode().equals("BACKLOG_GROWTH"));
    }

    @Test
    @DisplayName("후보가 maximum 10개로 제한되고 candidateRef가 연속으로 부여된다")
    void limitsCandidatesToMax10AndAssignsSequentialRef() {
        // Create 15 tasks matching various risk conditions
        List<SnapshotTask> tasks = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            String taskRef = String.format("TASK-%03d", i);
            tasks.add(new SnapshotTask(
                    taskRef,
                    "승인 후 미할당 지연 업무 " + i,
                    TaskStatus.TODO,
                    "HIGH",
                    null,
                    "2026-07-20T10:00:00Z",
                    "2026-07-25T18:00:00Z",
                    null,
                    DueState.OVERDUE,
                    new TaskChecklist(0, 3),
                    new TaskCollaboration(1, 1, 0),
                    new TaskHistory("TRANSITION", HoldReasonCategory.NONE, 0),
                    List.of("EVENT-01"),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            ));
        }

        ReportContext context = new ReportContext("GROUP-1", new SnapshotPeriod("2026-07-20", "2026-07-27", "Asia/Seoul"), "2026-07-27T00:00:00Z", Language.KO, "v7-2-prompt-001");
        SnapshotMetrics metrics = new SnapshotMetrics(15, 0, 0, 15, null);
        SnapshotWorkflow workflow = new SnapshotWorkflow(0, 15, 0, 0, 0, 0);

        AiWeeklyReportSnapshotV1 snapshot = new AiWeeklyReportSnapshotV1(
                "ai-weekly-report-snapshot.v1",
                context,
                metrics,
                SnapshotComparison.noBaseline(),
                workflow,
                List.of(),
                tasks,
                List.of(),
                List.of()
        );

        AiWeeklyReportSnapshotV1 evaluated = policyEngine.evaluate(snapshot);
        List<RiskCandidate> candidates = evaluated.riskCandidates();

        assertThat(candidates).hasSizeLessThanOrEqualTo(10);
        for (int i = 0; i < candidates.size(); i++) {
            String expectedRef = String.format("RISK-%03d", i + 1);
            assertThat(candidates.get(i).candidateRef()).isEqualTo(expectedRef);
        }
    }

    @Test
    @DisplayName("동일 입력에 대한 결과가 결정적이며 identical하다")
    void deterministicAndIdempotentEvaluation() throws IOException {
        InputStream stream = getClass().getResourceAsStream(SNAPSHOT_EXAMPLE);
        AiWeeklyReportSnapshotV1 initialSnapshot = json.readValue(stream, AiWeeklyReportSnapshotV1.class);

        AiWeeklyReportSnapshotV1 result1 = policyEngine.evaluate(initialSnapshot);
        AiWeeklyReportSnapshotV1 result2 = policyEngine.evaluate(initialSnapshot);

        assertThat(result1).isEqualTo(result2);
    }

    private Set<ValidationMessage> validate(String schemaResource, JsonNode document) {
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
