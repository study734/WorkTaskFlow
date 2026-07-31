package com.teamproject.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 주간 리포트 v7-2의 계약 기준선을 코드보다 먼저 고정한다. 여기서 검증하는 것은 Java 구현이
 * 아니라 계약 자체다. 구현이 없어도 이 테스트는 성립하며, Schema나 fixture를 실수로 바꾸면 깨진다.
 *
 * <p>Schema는 {@code docs/contracts/}가 외부 공유용 정본이고 {@code src/main/resources/ai/}가
 * 런타임 사본이다. 둘이 갈라지면 런타임과 문서가 다른 계약을 말하게 되므로 사본 동일성도 함께
 * 검사한다.
 */
class AiWeeklyReportSchemaFixtureTest {
    private static final String SNAPSHOT_SCHEMA = "/ai/ai-weekly-report-snapshot-v1.schema.json";
    private static final String ANALYSIS_SCHEMA = "/ai/ai-weekly-report-analysis-v1.schema.json";
    private static final String SNAPSHOT_EXAMPLE = "/ai/ai-weekly-report-snapshot-v1.example.json";
    private static final String ANALYSIS_EXAMPLE = "/ai/ai-weekly-report-analysis-v1.example.json";

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("Snapshot 예시가 ai-weekly-report-snapshot.v1 계약을 만족한다")
    void snapshotExampleSatisfiesItsContract() {
        assertThat(validate(SNAPSHOT_SCHEMA, read(SNAPSHOT_EXAMPLE))).isEmpty();
    }

    @Test
    @DisplayName("Analysis 예시가 ai-weekly-report-analysis.v1 계약을 만족한다")
    void analysisExampleSatisfiesItsContract() {
        assertThat(validate(ANALYSIS_SCHEMA, read(ANALYSIS_EXAMPLE))).isEmpty();
    }

    /**
     * fixture가 통과하는 것만으로는 검증기가 살아 있다는 증거가 되지 못한다. 필수 필드를 하나
     * 빼면 반드시 실패해야 한다.
     */
    @Test
    @DisplayName("Snapshot 계약이 필수 필드 누락을 거부한다")
    void snapshotContractRejectsAMissingRequiredField() {
        ObjectNode broken = (ObjectNode) read(SNAPSHOT_EXAMPLE);
        broken.remove("reportContext");

        assertThat(messages(validate(SNAPSHOT_SCHEMA, broken)))
                .anyMatch(message -> message.contains("reportContext"));
    }

    @Test
    @DisplayName("Analysis 계약이 필수 필드 누락을 거부한다")
    void analysisContractRejectsAMissingRequiredField() {
        ObjectNode broken = (ObjectNode) read(ANALYSIS_EXAMPLE);
        broken.remove("achievement");

        assertThat(messages(validate(ANALYSIS_SCHEMA, broken)))
                .anyMatch(message -> message.contains("achievement"));
    }

    /**
     * D1 확정 사항을 고정한다. 명세 초안이 쓰던 {@code COMPLETE}는 계약에 없는 값이며, Java 계약
     * 클래스가 실수로 그 값을 되살리면 이 테스트가 먼저 깨진다.
     */
    @Test
    @DisplayName("Analysis 계약이 폐기된 analysisStatus 값 COMPLETE를 거부한다")
    void analysisContractRejectsTheRetiredCompleteStatus() {
        ObjectNode broken = (ObjectNode) read(ANALYSIS_EXAMPLE);
        broken.put("analysisStatus", "COMPLETE");

        assertThat(messages(validate(ANALYSIS_SCHEMA, broken)))
                .anyMatch(message -> message.contains("analysisStatus"));
    }

    /** v7-2는 회의 안건을 최대 3개로 제한한다. 이 상한이 계약 수준에서 강제되는지 확인한다. */
    @Test
    @DisplayName("Analysis 계약이 4개째 이슈를 거부한다")
    void analysisContractRejectsAFourthIssue() {
        ObjectNode broken = (ObjectNode) read(ANALYSIS_EXAMPLE);
        ArrayNode issues = (ArrayNode) broken.get("issues");
        assertThat(issues).hasSize(3);
        issues.add(issues.get(0).deepCopy());

        assertThat(messages(validate(ANALYSIS_SCHEMA, broken)))
                .anyMatch(message -> message.contains("issues"));
    }

    /**
     * 두 Schema 모두 {@code additionalProperties: false}다. 명세 본문 예시에만 존재하고 계약에는
     * 없는 {@code policy} 같은 키를 서버가 실수로 전송하면 거부되어야 한다.
     */
    @Test
    @DisplayName("Snapshot 계약이 계약에 없는 최상위 키를 거부한다")
    void snapshotContractRejectsAnUndeclaredTopLevelKey() {
        ObjectNode broken = (ObjectNode) read(SNAPSHOT_EXAMPLE);
        broken.putObject("policy").put("maxIssues", 3);

        assertThat(messages(validate(SNAPSHOT_SCHEMA, broken)))
                .anyMatch(message -> message.contains("policy"));
    }

    /**
     * 런타임이 읽는 Schema와 저장소가 공개하는 Schema가 갈라지면, 테스트는 통과하는데 실제 호출은
     * 다른 계약으로 나가는 상태가 된다. 바이트 단위로 같아야 한다.
     */
    @Test
    @DisplayName("런타임 Schema 사본이 docs/contracts 정본과 동일하다")
    void runtimeSchemaCopiesMatchThePublishedContracts() {
        assertThat(classpathBytes(SNAPSHOT_SCHEMA))
                .isEqualTo(publishedBytes("ai-weekly-report-snapshot-v1.schema.json"));
        assertThat(classpathBytes(ANALYSIS_SCHEMA))
                .isEqualTo(publishedBytes("ai-weekly-report-analysis-v1.schema.json"));
    }

    private Set<ValidationMessage> validate(String schemaResource, JsonNode document) {
        // format assertion을 켜야 date / date-time이 실제로 검사된다. 2020-12 기본값은 annotation이다.
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .formatAssertionsEnabled(true)
                .build();
        JsonSchemaFactory factory =
                JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream schema = resource(schemaResource)) {
            JsonSchema compiled = factory.getSchema(schema, config);
            return compiled.validate(document);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private java.util.List<String> messages(Set<ValidationMessage> failures) {
        assertThat(failures).isNotEmpty();
        return failures.stream().map(ValidationMessage::toString).toList();
    }

    private JsonNode read(String resource) {
        try (InputStream stream = resource(resource)) {
            return json.readTree(stream);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private byte[] classpathBytes(String resource) {
        try (InputStream stream = resource(resource)) {
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** surefire의 basedir은 backend/ 이므로 저장소 루트의 docs/는 한 단계 위에 있다. */
    private byte[] publishedBytes(String fileName) {
        Path path = Path.of("..", "docs", "contracts", fileName);
        assertThat(path).exists();
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private InputStream resource(String name) {
        InputStream stream = getClass().getResourceAsStream(name);
        assertThat(stream).as("classpath resource %s", name).isNotNull();
        return stream;
    }
}
