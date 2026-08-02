package com.teamproject.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.Response;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.services.blocking.ResponseService;
import com.teamproject.report.application.AiWeeklyReportFallbackFactory;
import com.teamproject.report.application.AiWeeklyReportPolicyEngine;
import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.AiWeeklyReportAnalysisV1;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.AiWeeklyReportSnapshotV1;
import com.teamproject.report.infrastructure.openai.OpenAiAnalysisContractMapper;
import com.teamproject.report.infrastructure.openai.OpenAiReportExceptions.*;
import com.teamproject.report.infrastructure.openai.OpenAiReportProperties;
import com.teamproject.report.infrastructure.openai.OpenAiWeeklyReportGateway;
import com.teamproject.report.infrastructure.openai.contract.AiWeeklyReportAnalysisContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiWeeklyReportGatewayTest {

    private static final String MODEL = "gpt-4o";

    private final ObjectMapper json = new ObjectMapper();
    private final OpenAIClient client = mock(OpenAIClient.class);
    private final ResponseService responses = mock(ResponseService.class);
    private final OpenAiAnalysisContractMapper mapper = new OpenAiAnalysisContractMapper();
    private final AiWeeklyReportPolicyEngine policyEngine = new AiWeeklyReportPolicyEngine();
    private final AiWeeklyReportFallbackFactory fallbackFactory = new AiWeeklyReportFallbackFactory();

    private AiWeeklyReportSnapshotV1 snapshot;

    @BeforeEach
    void setUp() throws IOException {
        when(client.responses()).thenReturn(responses);

        InputStream stream = getClass().getResourceAsStream("/ai/ai-weekly-report-snapshot-v1.example.json");
        AiWeeklyReportSnapshotV1 raw = json.readValue(stream, AiWeeklyReportSnapshotV1.class);
        snapshot = policyEngine.evaluate(raw);
    }

    private OpenAiWeeklyReportGateway gateway(boolean enabled, String model) {
        OpenAiReportProperties props = new OpenAiReportProperties(
                enabled,
                "sk-test-key",
                model,
                "https://api.openai.com/v1",
                Duration.ofSeconds(45),
                1,
                3000L,
                "v7-2-prompt-001"
        );
        return new OpenAiWeeklyReportGateway(client, props, json, mapper);
    }

    @Test
    @DisplayName("정상 요청 시 Responses API에 model, store(false), maxOutputTokens, Structured Output 파라미터를 올바르게 전달한다")
    void sendsCorrectParametersToResponsesApi() throws Exception {
        AiWeeklyReportAnalysisV1 fallback = fallbackFactory.create(snapshot);
        String validContractJson = json.writeValueAsString(fallback);
        stubResponse(completed(validContractJson));

        gateway(true, MODEL).analyze(snapshot);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<StructuredResponseCreateParams<AiWeeklyReportAnalysisContract>> captor =
                ArgumentCaptor.forClass(StructuredResponseCreateParams.class);
        org.mockito.Mockito.verify(responses).create(captor.capture());

        StructuredResponseCreateParams<AiWeeklyReportAnalysisContract> params = captor.getValue();
        var raw = params.rawParams();

        assertThat(params.responseType()).isEqualTo(AiWeeklyReportAnalysisContract.class);
        assertThat(raw.store()).contains(false);
        assertThat(raw.maxOutputTokens()).contains(3000L);
        assertThat(raw.instructions().orElseThrow()).contains("당신은 팀 업무 회의를 지원하는 분석가다.");
    }

    /**
     * severity와 recommendedOptionCode가 String이던 동안 모델은 계약 밖 값을 돌려줄 수 있었고,
     * 매퍼의 {@code valueOf}가 IllegalArgumentException으로 터져 유료 응답 하나가 통째로
     * SERVER_FALLBACK이 됐다. fixture matrix의 group 20에서 실제로 관측했다.
     * 이제는 요청 스키마가 값 자체를 강제한다.
     */
    @Test
    @DisplayName("닫힌 코드 집합은 요청 스키마에서 enum으로 강제된다")
    void constrainsClosedCodeSetsInTheRequestSchema() throws Exception {
        AiWeeklyReportAnalysisV1 fallback = fallbackFactory.create(snapshot);
        stubResponse(completed(json.writeValueAsString(fallback)));

        gateway(true, MODEL).analyze(snapshot);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<StructuredResponseCreateParams<AiWeeklyReportAnalysisContract>> captor =
                ArgumentCaptor.forClass(StructuredResponseCreateParams.class);
        org.mockito.Mockito.verify(responses).create(captor.capture());

        String schema = captor.getValue().rawParams().text().orElseThrow().toString();
        assertThat(schema)
                .contains("REBALANCE_WORK")
                .contains("SET_NEXT_REVIEW_DATE")
                .contains("LEADER_DECISION_REQUIRED")
                .contains("WORKLOAD_CONCENTRATION")
                .contains("GROUP_ADMIN")
                .contains("SELECTED_MEMBER");
    }

    @Test
    @DisplayName("OpenAI 응답이 비활성화되었거나 모델이 없으면 OpenAiReportUnavailableException을 던진다")
    void throwsExceptionOnDisabledOrMissingModel() {
        OpenAiWeeklyReportGateway disabledGateway = gateway(false, MODEL);
        assertThatThrownBy(() -> disabledGateway.analyze(snapshot))
                .isInstanceOf(OpenAiReportUnavailableException.class);

        OpenAiWeeklyReportGateway noModelGateway = gateway(true, "");
        assertThatThrownBy(() -> noModelGateway.analyze(snapshot))
                .isInstanceOf(OpenAiReportUnavailableException.class);
    }

    @Test
    @DisplayName("OpenAI 응답에 output_text가 없으면 OpenAiReportInvalidResponseException을 던진다")
    void throwsExceptionWhenNoOutputText() throws Exception {
        stubResponse(withoutOutput());

        OpenAiWeeklyReportGateway gw = gateway(true, MODEL);
        assertThatThrownBy(() -> gw.analyze(snapshot))
                .isInstanceOf(OpenAiReportInvalidResponseException.class);
    }

    @Test
    @DisplayName("Rate limit (429) 예외 발생 시 OpenAiReportRateLimitException으로 변환된다")
    void handlesRateLimitException() {
        when(responses.create(any(StructuredResponseCreateParams.class)))
                .thenThrow(new RuntimeException("429 rate_limit exceeded"));

        OpenAiWeeklyReportGateway gw = gateway(true, MODEL);
        assertThatThrownBy(() -> gw.analyze(snapshot))
                .isInstanceOf(OpenAiReportRateLimitException.class);
    }

    @Test
    @DisplayName("5xx Provider Error 발생 시 OpenAiReportInvalidResponseException으로 변환된다")
    void handles5xxProviderError() {
        when(responses.create(any(StructuredResponseCreateParams.class)))
                .thenThrow(new RuntimeException("500 Internal Server Error"));

        OpenAiWeeklyReportGateway gw = gateway(true, MODEL);
        assertThatThrownBy(() -> gw.analyze(snapshot))
                .isInstanceOf(OpenAiReportInvalidResponseException.class);
    }

    @Test
    @DisplayName("Refusal 응답 수신 시 OpenAiReportInvalidResponseException을 던진다")
    void handlesRefusalResponse() throws Exception {
        stubResponse(refusal());

        OpenAiWeeklyReportGateway gw = gateway(true, MODEL);
        assertThatThrownBy(() -> gw.analyze(snapshot))
                .isInstanceOf(OpenAiReportInvalidResponseException.class);
    }

    @Test
    @DisplayName("Incomplete 응답 수신 시 OpenAiReportInvalidResponseException을 던진다")
    void handlesIncompleteResponse() throws Exception {
        stubResponse(withStatus(completed("{}"), "incomplete"));

        OpenAiWeeklyReportGateway gw = gateway(true, MODEL);
        assertThatThrownBy(() -> gw.analyze(snapshot))
                .isInstanceOf(OpenAiReportInvalidResponseException.class);
    }

    @Test
    @DisplayName("Timeout 예외 발생 시 OpenAiReportTimeoutException으로 변환된다")
    void handlesTimeoutException() {
        when(responses.create(any(StructuredResponseCreateParams.class)))
                .thenThrow(new RuntimeException("Request timeout"));

        OpenAiWeeklyReportGateway gw = gateway(true, MODEL);
        assertThatThrownBy(() -> gw.analyze(snapshot))
                .isInstanceOf(OpenAiReportTimeoutException.class);
    }

    private void stubResponse(String responseJson) throws IOException {
        Response sdkResponse = ObjectMappers.jsonMapper().readValue(responseJson, Response.class);
        StructuredResponse<AiWeeklyReportAnalysisContract> structured =
                new StructuredResponse<>(AiWeeklyReportAnalysisContract.class, sdkResponse);
        when(responses.create(any(StructuredResponseCreateParams.class))).thenReturn(structured);
    }

    private String completed(String outputText) throws IOException {
        return json.writeValueAsString(Map.of(
                "id", "resp_test_001",
                "object", "response",
                "created_at", 1785222000,
                "status", "completed",
                "model", MODEL,
                "output", List.of(Map.of(
                        "id", "msg_test",
                        "type", "message",
                        "role", "assistant",
                        "status", "completed",
                        "content", List.of(Map.of(
                                "type", "output_text",
                                "annotations", List.of(),
                                "text", outputText))))));
    }

    private String withoutOutput() throws IOException {
        return json.writeValueAsString(Map.of(
                "id", "resp_empty",
                "object", "response",
                "created_at", 1785222000,
                "status", "completed",
                "model", MODEL,
                "output", List.of()));
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

    private String withStatus(String responseJson, String status) throws IOException {
        var node = json.readTree(responseJson);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("status", status);
        return json.writeValueAsString(node);
    }

    /**
     * 프롬프트 리소스가 없으면 gateway가 조용히 내장 문구로 물러선다. 그 문구에는 validator
     * 규칙이 빠져 있어 결과가 전부 검증에 걸리고 모든 리포트가 SERVER_FALLBACK이 된다.
     * 실제로 겪은 증상이다. 확장자를 .txt에서 .prompt로 옮겼으므로 경로를 고정한다.
     * (.txt는 CI repository-safety가 추적을 거부한다.)
     */
    @Test
    @DisplayName("두 언어 프롬프트 리소스가 클래스패스에 있고 검증 규칙을 담고 있다")
    void keepsBothPromptResourcesOnTheClasspath() throws IOException {
        for (String language : List.of("ko", "en")) {
            String path = "/prompts/ai-weekly-report-v7-2-" + language + ".prompt";
            try (InputStream stream = getClass().getResourceAsStream(path)) {
                assertThat(stream).as("%s 리소스", path).isNotNull();
                String prompt = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertThat(prompt)
                        .contains("ai-weekly-report-analysis.v1")
                        .contains("GROUP_ADMIN")
                        .contains("TASK-12");
            }
        }
    }
}
