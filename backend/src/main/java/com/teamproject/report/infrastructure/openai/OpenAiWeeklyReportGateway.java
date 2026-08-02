package com.teamproject.report.infrastructure.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.AiWeeklyReportAnalysisV1;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.AiWeeklyReportSnapshotV1;
import com.teamproject.report.application.port.AiWeeklyReportGateway;
import com.teamproject.report.infrastructure.openai.OpenAiReportExceptions.*;
import com.teamproject.report.infrastructure.openai.contract.AiWeeklyReportAnalysisContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 공식 OpenAI Java SDK Responses API 기반 AI 주간 리포트 분석 Gateway 구현체 (M7).
 */
@Component
public class OpenAiWeeklyReportGateway implements AiWeeklyReportGateway {

    private static final Logger log = LoggerFactory.getLogger(OpenAiWeeklyReportGateway.class);

    private final OpenAIClient client;
    private final OpenAiReportProperties properties;
    private final ObjectMapper objectMapper;
    private final OpenAiAnalysisContractMapper mapper;

    public OpenAiWeeklyReportGateway(
            OpenAIClient client,
            OpenAiReportProperties properties,
            ObjectMapper objectMapper,
            OpenAiAnalysisContractMapper mapper
    ) {
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
    }

    @Override
    public Analysis analyze(AiWeeklyReportSnapshotV1 snapshot) {
        if (!properties.enabled()) {
            log.info("OpenAI report generation is disabled via properties");
            throw new OpenAiReportUnavailableException("OpenAI report is disabled");
        }
        if (!properties.hasModel()) {
            log.warn("OpenAI model is missing");
            throw new OpenAiReportUnavailableException("OpenAI model is missing");
        }
        // 키 없이 켜져 있으면 placeholder 키로 호출해 401을 받을 때까지 기다린다(최대 45초).
        // 결과는 어차피 fallback이므로 기다릴 이유가 없다.
        if (!properties.hasApiKey()) {
            log.warn("OpenAI API key is missing");
            throw new OpenAiReportUnavailableException("OpenAI API key is missing");
        }

        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Snapshot serialization failed", e);
        }

        String instructions = loadPrompt(snapshot.reportContext() != null && snapshot.reportContext().language() != null ? snapshot.reportContext().language().name() : "KO");

        StructuredResponseCreateParams<AiWeeklyReportAnalysisContract> params =
                ResponseCreateParams.builder()
                        .instructions(instructions)
                        .input(snapshotJson)
                        .text(AiWeeklyReportAnalysisContract.class)
                        .model(properties.model())
                        .maxOutputTokens(properties.maxOutputTokens())
                        .store(false)
                        .build();

        try {
            var response = client.responses().create(params);

            if (response.status().filter(com.openai.models.responses.ResponseStatus.COMPLETED::equals).isEmpty()) {
                throw new OpenAiReportInvalidResponseException("OpenAI response status is incomplete: " + response.status().map(Object::toString).orElse("NONE"));
            }

            AiWeeklyReportAnalysisContract contract = response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .findFirst()
                    .orElseThrow(() -> new OpenAiReportInvalidResponseException("No output_text returned from OpenAI Responses API"));

            Integer inputTokens = response.usage().map(u -> (int) u.inputTokens()).orElse(null);
            Integer outputTokens = response.usage().map(u -> (int) u.outputTokens()).orElse(null);
            return new Analysis(mapper.toDomain(contract), inputTokens, outputTokens);

        } catch (OpenAiReportException e) {
            log.warn("OpenAI weekly report call failed: category={}", e.getClass().getSimpleName());
            throw e;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("429") || msg.contains("rate_limit")) {
                log.warn("OpenAI rate limit error occurred");
                throw new OpenAiReportRateLimitException("Rate limit reached", e);
            }
            if (msg.contains("timeout") || msg.contains("Timeout")) {
                log.warn("OpenAI call timed out");
                throw new OpenAiReportTimeoutException("Request timed out", e);
            }
            // 예외 클래스만 남기면 왜 fallback으로 떨어졌는지 알 수 없다. 근본 원인 클래스까지
            // 남긴다. 메시지 본문은 응답 조각을 담을 수 있어 넣지 않는다(명세 8.2).
            log.warn("OpenAI report call failed: exception={} rootCause={}",
                    e.getClass().getSimpleName(), rootCauseName(e));
            throw new OpenAiReportInvalidResponseException("OpenAI response call failed", e);
        }
    }

    /** 원인 사슬의 마지막 클래스 이름. 값이나 메시지는 담지 않는다. */
    private String rootCauseName(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }

    /**
     * 확장자가 {@code .prompt}인 이유는 CI의 repository-safety가 추적된 {@code .txt}를 거부하기
     * 때문이다. 파일은 런타임 필수 리소스라 지울 수 없다.
     *
     * <p>내장 문구로 물러서면 validator 규칙이 빠진 짧은 프롬프트가 쓰이고, 결과가 검증에
     * 걸려 모든 리포트가 SERVER_FALLBACK으로 떨어진다. 실제로 겪은 증상이라 조용히 넘기지
     * 않고 남긴다.
     */
    private String loadPrompt(String language) {
        String resourceName = "/prompts/ai-weekly-report-v7-2-" + (language.equalsIgnoreCase("EN") ? "en" : "ko") + ".prompt";
        try (InputStream stream = getClass().getResourceAsStream(resourceName)) {
            if (stream != null) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            log.warn("Prompt resource missing, falling back to the built-in prompt: {}", resourceName);
        } catch (Exception e) {
            log.warn("Prompt resource unreadable, falling back to the built-in prompt: {} cause={}",
                    resourceName, e.getClass().getSimpleName());
        }

        return "당신은 팀 업무 회의를 지원하는 분석가다.\n" +
               "서버가 제공한 수치와 facts를 변경하거나 재계산하지 않는다.\n" +
               "서버가 제공한 riskCandidates 안에서만 이슈를 선택한다.\n" +
               "성과는 최대 1개, 이슈와 결정은 최대 3개다.\n" +
               "결정 옵션, 실행 단계, 완료 조건은 서버 허용 목록에서만 선택한다.\n" +
               "JSON Schema를 엄격히 준수한다.";
    }
}
