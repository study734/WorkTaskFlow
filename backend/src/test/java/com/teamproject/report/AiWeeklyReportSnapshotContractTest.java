package com.teamproject.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.report.application.dto.AiWeeklyReportDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Java Snapshot 계약이 저장 JSON Schema와 어긋나지 않는지 확인한다. record component 이름이
 * 곧 직렬화 key이므로 이름 하나만 바뀌어도 OpenAI 입력 계약이 깨진다.
 *
 * <p>{@code AiWeeklyReportSchemaFixtureTest}가 fixture ↔ Schema를 검증한다면, 이 테스트는
 * Java 타입 ↔ Schema를 검증한다.
 */
class AiWeeklyReportSnapshotContractTest {
    private static final String SNAPSHOT_SCHEMA = "/ai/ai-weekly-report-snapshot-v1.schema.json";

    private final ObjectMapper json = new ObjectMapper();
    private final JsonNode schema = readSchema();

    @Test
    @DisplayName("schemaVersion 상수가 계약 식별자와 같다")
    void declaresContractSchemaVersion() {
        assertThat(AiWeeklyReportDtos.SNAPSHOT_SCHEMA_VERSION)
                .isEqualTo(schema.at("/properties/schemaVersion/const").asText());
    }

    @Test
    @DisplayName("최상위 record component가 Schema의 required 집합과 일치한다")
    void topLevelComponentsMatchSchema() {
        assertThat(componentsOf(AiWeeklyReportDtos.AiWeeklyReportSnapshotV1.class))
                .containsExactlyInAnyOrderElementsOf(requiredAt(""));
    }

    @Test
    @DisplayName("중첩 객체의 record component가 Schema와 일치한다")
    void nestedComponentsMatchSchema() {
        assertThat(componentsOf(AiWeeklyReportDtos.ReportContext.class))
                .containsExactlyInAnyOrderElementsOf(requiredAt("/properties/reportContext"));
        assertThat(componentsOf(AiWeeklyReportDtos.SnapshotPeriod.class))
                .containsExactlyInAnyOrderElementsOf(
                        requiredAt("/properties/reportContext/properties/period"));
        assertThat(componentsOf(AiWeeklyReportDtos.SnapshotMetrics.class))
                .containsExactlyInAnyOrderElementsOf(requiredAt("/properties/metrics"));
        assertThat(componentsOf(AiWeeklyReportDtos.SnapshotComparison.class))
                .containsExactlyInAnyOrderElementsOf(requiredAt("/properties/comparison"));
        assertThat(componentsOf(AiWeeklyReportDtos.SnapshotWorkflow.class))
                .containsExactlyInAnyOrderElementsOf(requiredAt("/properties/workflow"));
        assertThat(componentsOf(AiWeeklyReportDtos.SnapshotMember.class))
                .containsExactlyInAnyOrderElementsOf(requiredAt("/properties/members/items"));
        assertThat(componentsOf(AiWeeklyReportDtos.SnapshotTask.class))
                .containsExactlyInAnyOrderElementsOf(requiredAt("/properties/tasks/items"));
        assertThat(componentsOf(AiWeeklyReportDtos.CalendarConstraint.class))
                .containsExactlyInAnyOrderElementsOf(
                        requiredAt("/properties/calendarConstraints/items"));
        assertThat(componentsOf(AiWeeklyReportDtos.RiskCandidate.class))
                .containsExactlyInAnyOrderElementsOf(
                        requiredAt("/properties/riskCandidates/items"));
    }

    @Test
    @DisplayName("업무 하위 객체의 record component가 Schema와 일치한다")
    void taskChildComponentsMatchSchema() {
        String task = "/properties/tasks/items/properties/";
        assertThat(componentsOf(AiWeeklyReportDtos.TaskChecklist.class))
                .containsExactlyInAnyOrderElementsOf(requiredAt(task + "checklist"));
        assertThat(componentsOf(AiWeeklyReportDtos.TaskCollaboration.class))
                .containsExactlyInAnyOrderElementsOf(requiredAt(task + "collaboration"));
        assertThat(componentsOf(AiWeeklyReportDtos.TaskHistory.class))
                .containsExactlyInAnyOrderElementsOf(requiredAt(task + "history"));
    }

    @Test
    @DisplayName("enum 값 집합이 Schema enum과 정확히 일치한다")
    void enumValuesMatchSchema() {
        String task = "/properties/tasks/items/properties/";
        assertThat(namesOf(AiWeeklyReportDtos.TaskStatus.class))
                .containsExactlyInAnyOrderElementsOf(enumAt(task + "status"));
        assertThat(namesOf(AiWeeklyReportDtos.DueState.class))
                .containsExactlyInAnyOrderElementsOf(enumAt(task + "dueState"));
        assertThat(namesOf(AiWeeklyReportDtos.HoldReasonCategory.class))
                .containsExactlyInAnyOrderElementsOf(
                        enumAt(task + "history/properties/holdReasonCategory"));
        assertThat(namesOf(AiWeeklyReportDtos.SignalCode.class))
                .containsExactlyInAnyOrderElementsOf(enumAt(task + "signalCodes/items"));
        assertThat(namesOf(AiWeeklyReportDtos.DecisionOptionCode.class))
                .containsExactlyInAnyOrderElementsOf(
                        enumAt(task + "allowedDecisionOptionCodes/items"));
        assertThat(namesOf(AiWeeklyReportDtos.ExecutionStepCode.class))
                .containsExactlyInAnyOrderElementsOf(
                        enumAt(task + "allowedExecutionStepCodes/items"));
        assertThat(namesOf(AiWeeklyReportDtos.CompletionSignalCode.class))
                .containsExactlyInAnyOrderElementsOf(
                        enumAt(task + "allowedCompletionSignalCodes/items"));
        assertThat(namesOf(AiWeeklyReportDtos.Language.class))
                .containsExactlyInAnyOrderElementsOf(
                        enumAt("/properties/reportContext/properties/language"));
        assertThat(namesOf(AiWeeklyReportDtos.ComparisonStatus.class))
                .containsExactlyInAnyOrderElementsOf(
                        enumAt("/properties/comparison/properties/status"));
        assertThat(namesOf(AiWeeklyReportDtos.Severity.class))
                .containsExactlyInAnyOrderElementsOf(
                        enumAt("/properties/riskCandidates/items/properties/severity"));
    }

    /**
     * 계약에 원문 문자열을 담을 자리가 없어야 한다. 필드가 하나라도 생기면 개인정보 경계가
     * 조용히 뚫린다.
     */
    @Test
    @DisplayName("계약 어디에도 제목·설명·이름 원문을 담는 필드가 없다")
    void carriesNoRawTextField() {
        Set<String> forbidden = Set.of(
                "title", "description", "content", "holdReason", "stopReason",
                "assigneeName", "memberName", "nickname", "email", "userId");

        Stream.of(AiWeeklyReportDtos.AiWeeklyReportSnapshotV1.class,
                        AiWeeklyReportDtos.ReportContext.class,
                        AiWeeklyReportDtos.SnapshotMember.class,
                        AiWeeklyReportDtos.SnapshotTask.class,
                        AiWeeklyReportDtos.TaskCollaboration.class,
                        AiWeeklyReportDtos.TaskHistory.class,
                        AiWeeklyReportDtos.CalendarConstraint.class)
                .forEach(type -> assertThat(componentsOf(type))
                        .as("%s", type.getSimpleName())
                        .doesNotContainAnyElementsOf(forbidden));
    }

    @Test
    @DisplayName("비교 기준이 없으면 delta 없이 NO_BASELINE만 남는다")
    void noBaselineComparisonCarriesNoDelta() {
        AiWeeklyReportDtos.SnapshotComparison comparison =
                AiWeeklyReportDtos.SnapshotComparison.noBaseline();

        assertThat(comparison.status())
                .isEqualTo(AiWeeklyReportDtos.ComparisonStatus.NO_BASELINE);
        assertThat(comparison.previousFrom()).isNull();
        assertThat(comparison.previousToExclusive()).isNull();
        assertThat(comparison.periodTaskCountDelta()).isNull();
        assertThat(comparison.completionRatePointDelta()).isNull();
        assertThat(comparison.onTimeRatePointDelta()).isNull();
        assertThat(comparison.delayedCountDelta()).isNull();
    }

    private List<String> componentsOf(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName).toList();
    }

    private List<String> namesOf(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
    }

    private List<String> requiredAt(String pointer) {
        JsonNode node = schema.at(pointer + "/required");
        assertThat(node.isArray()).as("required at %s", pointer).isTrue();
        return toList(node);
    }

    private List<String> enumAt(String pointer) {
        JsonNode node = schema.at(pointer + "/enum");
        assertThat(node.isArray()).as("enum at %s", pointer).isTrue();
        return toList(node);
    }

    private List<String> toList(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asText).toList();
    }

    private JsonNode readSchema() {
        try (InputStream stream = getClass().getResourceAsStream(SNAPSHOT_SCHEMA)) {
            assertThat(stream).as("classpath %s", SNAPSHOT_SCHEMA).isNotNull();
            return json.readTree(stream);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
