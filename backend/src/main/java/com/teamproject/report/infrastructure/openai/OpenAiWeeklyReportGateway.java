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
    public AiWeeklyReportAnalysisV1 analyze(AiWeeklyReportSnapshotV1 snapshot) {
        if (!properties.enabled()) {
            log.info("OpenAI report generation is disabled via properties");
            throw new OpenAiReportUnavailableException("OpenAI report is disabled");
        }
        if (!properties.hasModel()) {
            log.warn("OpenAI model is missing");
            throw new OpenAiReportUnavailableException("OpenAI model is missing");
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

            AiWeeklyReportAnalysisContract contract = response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .findFirst()
                    .orElseThrow(() -> new OpenAiReportInvalidResponseException("No output_text returned from OpenAI Responses API"));

            return mapper.toDomain(contract);

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
            log.warn("OpenAI report call failed with exception: {}", e.getClass().getSimpleName());
            throw new OpenAiReportInvalidResponseException("OpenAI response call failed", e);
        }
    }

    private String loadPrompt(String language) {
        String resourceName = "/prompts/ai-weekly-report-v7-2-" + (language.equalsIgnoreCase("EN") ? "en" : "ko") + ".txt";
        try (InputStream stream = getClass().getResourceAsStream(resourceName)) {
            if (stream != null) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}

        return "당신은 팀 업무 회의를 지원하는 분석가다.\n" +
               "서버가 제공한 수치와 facts를 변경하거나 재계산하지 않는다.\n" +
               "서버가 제공한 riskCandidates 안에서만 이슈를 선택한다.\n" +
               "성과는 최대 1개, 이슈와 결정은 최대 3개다.\n" +
               "결정 옵션, 실행 단계, 완료 조건은 서버 허용 목록에서만 선택한다.\n" +
               "JSON Schema를 엄격히 준수한다.";
    }
}
