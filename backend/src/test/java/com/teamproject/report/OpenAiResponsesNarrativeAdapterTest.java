package com.teamproject.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.ObjectMappers;
import com.openai.core.http.Headers;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.services.blocking.ResponseService;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.report.application.NarrativeContract;
import com.teamproject.report.application.NarrativeContract.GeneratedNarrative;
import com.teamproject.report.application.ReportContracts.*;
import com.teamproject.report.infrastructure.OpenAiResponsesNarrativeAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 공식 SDK 경계에서 adapter를 검증한다. 이전 판은 {@code com.sun.net.httpserver.HttpServer}로
 * 임의 포트에 로컬 서버를 띄웠는데, 그 바인딩이 실패하는 환경에서는 테스트 5건이 통째로
 * 실행되지 않았다(study734/WorkTaskFlow#3). 이제 네트워크를 전혀 쓰지 않는다.
 *
 * <p>대신 {@link OpenAIClient}와 {@link ResponseService}만 mock하고, 응답은 SDK의 실제 wire
 * JSON을 {@link Response}로 역직렬화한 뒤 {@link StructuredResponse}의 public 생성자로 감싸
 * 만든다. 따라서 응답 파싱 경로(중첩 output/message/content 탐색, Structured Output
 * 역직렬화, refusal 판별, usage 집계)는 여전히 SDK의 진짜 구현이 수행한다. mock은 "어떤
 * 요청이 나갔는가"와 "어떤 응답·예외가 돌아왔는가"라는 경계에만 쓴다.
 */
class OpenAiResponsesNarrativeAdapterTest {
    private static final String MODEL = "gpt-5.6-luna";

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final NarrativeContract contract = new NarrativeContract(json);
    private final OpenAIClient client = mock(OpenAIClient.class);
    private final ResponseService responses = mock(ResponseService.class);

    // 1. 요청 구성
    @Test
    @DisplayName("정상 요청은 model·store(false)·maxOutputTokens·instructions·input과 Structured Output 타입을 담는다")
    void sendsStatelessStructuredRequest() throws Exception {
        stubResponse(completed(validNarrativeJson()));

        adapter(true).generate(input());

        StructuredResponseCreateParams<GeneratedNarrative> params = captureParams();
        var raw = params.rawParams();

        assertThat(params.responseType()).isEqualTo(GeneratedNarrative.class);
        assertThat(modelOf(raw.model().orElseThrow())).isEqualTo(MODEL);
        assertThat(raw.store()).contains(false);
        assertThat(raw.maxOutputTokens()).contains(5000L);

        assertThat(raw.instructions().orElseThrow())
                .contains("project operations analyst and chief of staff")
                .contains("paid weekly decision brief")
                .contains("dominant change, operational consequence, decision")
                .contains("Never use generic filler")
                .contains("Never invent or calculate a number or date");

        String textConfig = ObjectMappers.jsonMapper()
                .writeValueAsString(raw.text().orElseThrow());
        assertThat(textConfig)
                .contains("json_schema")
                .contains("\"strict\":true")
                .contains("headlineTemplate");
    }

    /**
     * provider에 넘어가는 입력은 계약이 만든 문자열 그대로여야 한다. 특히 팀원별 지표는
     * {@code writeAiContext}가 지우므로 직렬화 결과에 남으면 안 된다.
     */
    @Test
    @DisplayName("input은 계약이 만든 provider-safe 컨텍스트이며 팀원별 지표를 담지 않는다")
    void sendsProviderSafeContextAsInput() throws Exception {
        stubResponse(completed(validNarrativeJson()));
        AiGenerationInput generationInput = input();

        adapter(true).generate(generationInput);

        String sentInput = captureParams().rawParams().input().orElseThrow().asText();
        assertThat(sentInput).isEqualTo(contract.writeAiContext(generationInput.context()));
        assertThat(sentInput)
                .contains("tasks.total")
                .contains("MEMBER-99")
                .doesNotContain("memberLabel");
    }

    // 2. 정상 응답 파싱
    @Test
    @DisplayName("완료 응답에서 narrative와 model·token usage를 읽는다")
    void parsesCompletedStructuredResponse() throws Exception {
        stubResponse(completed(validNarrativeJson()));

        AiGenerationResult result = adapter(true).generate(input());

        assertThat(result.narrative().headlineTemplate()).isEqualTo("실행 중심 주간 요약");
        assertThat(result.narrative().topActions()).hasSize(1);
        assertThat(result.narrative().topActions().get(0).ownerRef()).isEqualTo("MEMBER-01");
        assertThat(result.model()).isEqualTo("gpt-5.6-response-revision");
        assertThat(result.inputTokens()).isEqualTo(100);
        assertThat(result.outputTokens()).isEqualTo(50);
        assertThat(result.totalTokens()).isEqualTo(150);
    }

    // 3. refusal
    @Test
    @DisplayName("refusal 응답은 AI_REPORT_REFUSED로 매핑한다")
    void mapsRefusalToSafeError() throws Exception {
        stubResponse(refusal());

        assertFailure("AI_REPORT_REFUSED", HttpStatus.BAD_GATEWAY);
    }

    // 4. incomplete
    @Test
    @DisplayName("incomplete 응답은 AI_REPORT_INCOMPLETE로 매핑한다")
    void mapsIncompleteResponseToSafeError() throws Exception {
        stubResponse(withStatus(completed(validNarrativeJson()), "incomplete"));

        assertFailure("AI_REPORT_INCOMPLETE", HttpStatus.BAD_GATEWAY);
    }

    // 5. outputText 없음 또는 malformed
    @Test
    @DisplayName("output이 비었거나 structured output이 깨지면 AI_REPORT_RESPONSE_INVALID로 매핑한다")
    void mapsMissingAndMalformedStructuredOutputToSafeError() throws Exception {
        stubResponse(withoutOutput());
        assertFailure("AI_REPORT_RESPONSE_INVALID", HttpStatus.BAD_GATEWAY);

        stubResponse(completed("not-json"));
        assertFailure("AI_REPORT_RESPONSE_INVALID", HttpStatus.BAD_GATEWAY);

        stubThrow(new OpenAIInvalidDataException("structured output could not be parsed"));
        assertFailure("AI_REPORT_RESPONSE_INVALID", HttpStatus.BAD_GATEWAY);
    }

    // 6. rate limit / provider 오류
    @Test
    @DisplayName("rate limit과 provider 오류는 재시도 없이 AI_REPORT_PROVIDER_UNAVAILABLE로 매핑한다")
    void mapsRateLimitAndProviderFailures() {
        stubThrow(RateLimitException.builder().headers(Headers.builder().build()).build());
        assertFailure("AI_REPORT_PROVIDER_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);

        stubThrow(InternalServerException.builder()
                .statusCode(502).headers(Headers.builder().build()).build());
        assertFailure("AI_REPORT_PROVIDER_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);

        stubThrow(new OpenAIIoException("connection reset"));
        assertFailure("AI_REPORT_PROVIDER_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
    }

    // 7. timeout
    @Test
    @DisplayName("timeout 원인이 실린 IO 오류는 AI_REPORT_TIMEOUT으로 매핑한다")
    void mapsTimeoutToGatewayTimeout() {
        stubThrow(new OpenAIIoException("request timed out",
                new SocketTimeoutException("read timed out")));
        assertFailure("AI_REPORT_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT);

        // SDK가 중첩해 감싸는 경우에도 원인 사슬을 따라가야 한다.
        stubThrow(new OpenAIIoException("request timed out",
                new IOException("wrapped", new InterruptedIOException("interrupted"))));
        assertFailure("AI_REPORT_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT);
    }

    // 8. 비활성화
    @Test
    @DisplayName("OpenAI가 비활성화되면 client를 한 번도 호출하지 않는다")
    void rejectsCallsWhenOpenAiIsNotConfigured() {
        assertThatThrownBy(() -> adapter(false).generate(input()))
                .isInstanceOfSatisfying(ApplicationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("AI_REPORT_NOT_CONFIGURED");
                    assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });

        verifyNoInteractions(client);
    }

    private void assertFailure(String code, HttpStatus status) {
        assertThatThrownBy(() -> adapter(true).generate(input()))
                .isInstanceOfSatisfying(ApplicationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(code);
                    assertThat(exception.status()).isEqualTo(status);
                });
    }

    private OpenAiResponsesNarrativeAdapter adapter(boolean enabled) {
        return new OpenAiResponsesNarrativeAdapter(client, contract, enabled, MODEL);
    }

    /** 실제 wire JSON을 SDK 타입으로 되살려 응답 파싱 경로를 진짜 구현에 맡긴다. */
    private void stubResponse(String responseJson) throws IOException {
        Response response = ObjectMappers.jsonMapper().readValue(responseJson, Response.class);
        StructuredResponse<GeneratedNarrative> structured =
                new StructuredResponse<>(GeneratedNarrative.class, response);
        when(client.responses()).thenReturn(responses);
        when(responses.create(any(StructuredResponseCreateParams.class))).thenReturn(structured);
    }

    private void stubThrow(RuntimeException failure) {
        when(client.responses()).thenReturn(responses);
        when(responses.create(any(StructuredResponseCreateParams.class))).thenThrow(failure);
    }

    @SuppressWarnings("unchecked")
    private StructuredResponseCreateParams<GeneratedNarrative> captureParams() {
        ArgumentCaptor<StructuredResponseCreateParams<GeneratedNarrative>> captor =
                ArgumentCaptor.forClass(StructuredResponseCreateParams.class);
        org.mockito.Mockito.verify(responses).create(captor.capture());
        return captor.getValue();
    }

    private String modelOf(ResponsesModel value) {
        if (value.isString()) return value.asString();
        if (value.isChat()) return value.asChat().asString();
        return value.toString();
    }

    private String completed(String outputText) throws IOException {
        return json.writeValueAsString(Map.of(
                "id", "resp_test",
                "object", "response",
                "created_at", 1785222000,
                "status", "completed",
                "model", "gpt-5.6-response-revision",
                "output", List.of(Map.of(
                        "id", "msg_test",
                        "type", "message",
                        "role", "assistant",
                        "status", "completed",
                        "content", List.of(Map.of(
                                "type", "output_text",
                                "annotations", List.of(),
                                "text", outputText)))),
                "usage", usage()));
    }

    private String withStatus(String responseJson, String status) throws IOException {
        var node = json.readTree(responseJson);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("status", status);
        return json.writeValueAsString(node);
    }

    private String withoutOutput() throws IOException {
        return json.writeValueAsString(Map.of(
                "id", "resp_empty",
                "object", "response",
                "created_at", 1785222000,
                "status", "completed",
                "model", "gpt-5.6-response-revision",
                "output", List.of(),
                "usage", usage()));
    }

    private String refusal() throws IOException {
        return json.writeValueAsString(Map.of(
                "id", "resp_refusal",
                "object", "response",
                "created_at", 1785222000,
                "status", "completed",
                "model", MODEL,
                "output", List.of(Map.of(
                        "id", "msg_refusal",
                        "type", "message",
                        "role", "assistant",
                        "status", "completed",
                        "content", List.of(Map.of(
                                "type", "refusal",
                                "refusal", "blocked"))))));
    }

    private Map<String, Object> usage() {
        return Map.of(
                "input_tokens", 100,
                "input_tokens_details", Map.of("cached_tokens", 0),
                "output_tokens", 50,
                "output_tokens_details", Map.of("reasoning_tokens", 0),
                "total_tokens", 150);
    }

    private String validNarrativeJson() throws IOException {
        return json.writeValueAsString(Map.of(
                "headlineTemplate", "실행 중심 주간 요약",
                "summary", item("진행할 업무가 있습니다."),
                "changes", List.of(),
                "achievements", List.of(),
                "risks", List.of(),
                "topActions", List.of(Map.of(
                        "priority", "P1",
                        "actionTemplate", "진행 업무를 우선 점검하세요.",
                        "reasonTemplate", "확정된 업무량을 실행 계획에 반영해야 합니다.",
                        "ownerRef", "MEMBER-01",
                        "evidenceKeys", List.of("tasks.total"),
                        "taskRefs", List.of(),
                        "objectiveRefs", List.of())),
                "leaderDecisions", List.of(),
                "limitations", List.of()));
    }

    private Map<String, Object> item(String text) {
        return Map.of(
                "textTemplate", text,
                "evidenceKeys", List.of("tasks.total"),
                "taskRefs", List.of(),
                "objectiveRefs", List.of());
    }

    private AiGenerationInput input() {
        return new AiGenerationInput(metrics(), "ko");
    }

    private MetricsSnapshot metrics() {
        return new MetricsSnapshot(
                LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19), 1,
                new StatusMetrics(0, 1, 0, 0, 0, 0, 0, 0),
                0, null, null,
                new HistoryCoverage(
                        HistoryCoverageStatus.COMPLETE,
                        Instant.parse("2026-07-01T00:00:00Z")),
                new ChecklistMetrics(0, 0, null),
                List.of(),
                // 팀원별 지표는 provider로 나가면 안 된다. ref만 남고 이 수치는 지워져야 한다.
                List.of(new MemberMetric("MEMBER-99", 7, 3, 4, 1, 80)),
                List.of(), Map.of("tasks.total", 1));
    }
}
