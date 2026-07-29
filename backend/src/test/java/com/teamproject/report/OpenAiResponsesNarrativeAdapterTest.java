package com.teamproject.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.report.application.NarrativeContract;
import com.teamproject.report.application.ReportContracts.*;
import com.teamproject.report.infrastructure.OpenAiResponsesNarrativeAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiResponsesNarrativeAdapterTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final NarrativeContract contract = new NarrativeContract(json);
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsOfficialSdkStatelessStructuredRequestAndParsesNarrative() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        start(exchange -> {
            requestBody.set(read(exchange));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, completed(validNarrativeJson()));
        });

        AiGenerationResult result = adapter(Duration.ofSeconds(2), true)
                .generate(new AiGenerationInput(metrics(), "ko"));

        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        assertThat(requestBody.get())
                .contains("\"store\":false")
                .contains("\"type\":\"json_schema\"")
                .contains("\"strict\":true")
                .contains("\"headlineTemplate\"")
                .contains("project operations analyst and chief of staff")
                .contains("paid weekly decision brief")
                .contains("dominant change, operational consequence, decision")
                .contains("Never use generic filler")
                .contains("Never invent or calculate a number or date")
                .doesNotContain("외부 전송 금지 제목");
        assertThat(result.model()).isEqualTo("gpt-5.6-response-revision");
        assertThat(result.totalTokens()).isEqualTo(150);
        assertThat(result.narrative().headlineTemplate()).isEqualTo("실행 중심 주간 요약");
    }

    @Test
    void mapsRefusalAndMalformedStructuredOutputToSafeErrors() throws Exception {
        assertFailure(refusal(), "AI_REPORT_REFUSED");
        assertFailure(completed("not-json"), "AI_REPORT_RESPONSE_INVALID");
    }

    @Test
    void mapsRateLimitAndProviderFailuresWithoutRetrying() throws Exception {
        assertProviderFailure(429);
        assertProviderFailure(502);
    }

    @Test
    void mapsTimeoutToGatewayTimeout() throws Exception {
        start(exchange -> {
            read(exchange);
            try {
                Thread.sleep(250);
                respond(exchange, 200, completed(validNarrativeJson()));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The SDK closes the timed-out exchange.
            }
        });

        assertThatThrownBy(() -> adapter(Duration.ofMillis(50), true).generate(input()))
                .isInstanceOfSatisfying(ApplicationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("AI_REPORT_TIMEOUT");
                    assertThat(exception.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                });
    }

    @Test
    void rejectsCallsWhenOpenAiIsNotConfigured() throws Exception {
        start(exchange -> respond(exchange, 500, "{}"));

        assertThatThrownBy(() -> adapter(Duration.ofSeconds(1), false).generate(input()))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("AI_REPORT_NOT_CONFIGURED"));
    }

    private void assertFailure(String response, String code) throws Exception {
        start(exchange -> {
            read(exchange);
            respond(exchange, 200, response);
        });
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(2), true).generate(input()))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
        stopServer();
        server = null;
    }

    private void assertProviderFailure(int status) throws Exception {
        start(exchange -> {
            read(exchange);
            respond(exchange, status, "{}");
        });
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(2), true).generate(input()))
                .isInstanceOfSatisfying(ApplicationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("AI_REPORT_PROVIDER_UNAVAILABLE");
                    assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
        stopServer();
        server = null;
    }

    private OpenAiResponsesNarrativeAdapter adapter(Duration timeout, boolean enabled) {
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey("test-key")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                .timeout(timeout)
                .maxRetries(0)
                .build();
        return new OpenAiResponsesNarrativeAdapter(client, contract, enabled, "gpt-5.6-luna");
    }

    private void start(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> handler.handle(exchange));
        server.start();
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
                "usage", Map.of(
                        "input_tokens", 100,
                        "input_tokens_details", Map.of("cached_tokens", 0),
                        "output_tokens", 50,
                        "output_tokens_details", Map.of("reasoning_tokens", 0),
                        "total_tokens", 150)));
    }

    private String refusal() throws IOException {
        return json.writeValueAsString(Map.of(
                "id", "resp_refusal",
                "object", "response",
                "created_at", 1785222000,
                "status", "completed",
                "model", "gpt-5.6-luna",
                "output", List.of(Map.of(
                        "id", "msg_refusal",
                        "type", "message",
                        "role", "assistant",
                        "status", "completed",
                        "content", List.of(Map.of(
                                "type", "refusal",
                                "refusal", "blocked"))))));
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

    private String read(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
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
                List.of(), List.of(), List.of(), Map.of("tasks.total", 1));
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
