package com.teamproject.report.infrastructure;

import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseUsage;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputMessage;
import com.openai.models.ResponsesModel;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.report.application.AiNarrativeGenerator;
import com.teamproject.report.application.NarrativeContract;
import com.teamproject.report.application.NarrativeContract.GeneratedNarrative;
import com.teamproject.report.application.ReportContracts.AiGenerationInput;
import com.teamproject.report.application.ReportContracts.AiGenerationResult;
import com.teamproject.report.application.ReportContracts.Narrative;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.io.InterruptedIOException;

@Component
public class OpenAiResponsesNarrativeAdapter implements AiNarrativeGenerator {
    private static final Logger log =
            LoggerFactory.getLogger(OpenAiResponsesNarrativeAdapter.class);

    private final OpenAIClient client;
    private final NarrativeContract contract;
    private final boolean enabled;
    private final String model;

    @Autowired
    public OpenAiResponsesNarrativeAdapter(
            @Qualifier("openAiReportClient") OpenAIClient client,
            NarrativeContract contract,
            @Value("${app.ai-report.enabled:false}") boolean enabled,
            @Value("${app.ai-report.api-key:}") String apiKey,
            @Value("${app.ai-report.model:gpt-5.6-luna}") String model) {
        this(client, contract, enabled && !apiKey.isBlank(), model);
    }

    public OpenAiResponsesNarrativeAdapter(
            OpenAIClient client, NarrativeContract contract, boolean enabled, String model) {
        this.client = client;
        this.contract = contract;
        this.enabled = enabled;
        this.model = model;
    }

    @Override
    public AiGenerationResult generate(AiGenerationInput input) {
        if (!enabled) {
            throw new ApplicationException("AI_REPORT_NOT_CONFIGURED",
                    HttpStatus.SERVICE_UNAVAILABLE, "AI 리포트 연동이 설정되지 않았습니다.");
        }
        try {
            StructuredResponseCreateParams<GeneratedNarrative> request =
                    ResponseCreateParams.builder()
                            .model(model)
                            .store(false)
                            .maxOutputTokens(1600)
                            .instructions(contract.instructions(input.language()))
                            .input(contract.writeAiContext(input.context()))
                            .text(contract.responseType())
                            .build();
            StructuredResponse<GeneratedNarrative> response =
                    client.responses().create(request);
            return parse(response);
        } catch (ApplicationException exception) {
            throw exception;
        } catch (OpenAIIoException exception) {
            if (causedByTimeout(exception)) {
                log.warn("event=AI_REPORT_CALL outcome=TIMEOUT");
                throw new ApplicationException("AI_REPORT_TIMEOUT",
                        HttpStatus.GATEWAY_TIMEOUT, "AI 리포트 생성 시간이 초과되었습니다.");
            }
            log.warn("event=AI_REPORT_CALL outcome=IO_ERROR");
            throw providerUnavailable();
        } catch (OpenAIInvalidDataException exception) {
            log.warn("event=AI_REPORT_CALL outcome=INVALID_RESPONSE");
            throw invalid("AI_REPORT_RESPONSE_INVALID");
        } catch (OpenAIException exception) {
            log.warn("event=AI_REPORT_CALL outcome=PROVIDER_ERROR");
            throw providerUnavailable();
        } catch (RuntimeException exception) {
            log.warn("event=AI_REPORT_CALL outcome=INVALID_RESPONSE");
            throw invalid("AI_REPORT_RESPONSE_INVALID");
        }
    }

    private AiGenerationResult parse(StructuredResponse<GeneratedNarrative> response) {
        if (response == null
                || response.status().filter(ResponseStatus.COMPLETED::equals).isEmpty()) {
            throw invalid("AI_REPORT_INCOMPLETE");
        }
        GeneratedNarrative generated = null;
        for (var output : response.output()) {
            if (output.message().isEmpty()) continue;
            for (StructuredResponseOutputMessage.Content<GeneratedNarrative> content
                    : output.message().orElseThrow().content()) {
                if (content.refusal().isPresent()) {
                    throw invalid("AI_REPORT_REFUSED");
                }
                if (content.outputText().isPresent()) {
                    generated = content.outputText().orElseThrow();
                }
            }
        }
        if (generated == null) throw invalid("AI_REPORT_RESPONSE_INVALID");

        Narrative narrative = contract.fromGenerated(generated);
        ResponseUsage usage = response.usage().orElse(null);
        return new AiGenerationResult(narrative, responseModel(response.model()),
                usage == null ? 0 : Math.toIntExact(usage.inputTokens()),
                usage == null ? 0 : Math.toIntExact(usage.outputTokens()),
                usage == null ? 0 : Math.toIntExact(usage.totalTokens()));
    }

    private String responseModel(ResponsesModel value) {
        if (value.isString()) return value.asString();
        if (value.isChat()) return value.asChat().asString();
        if (value.isOnly()) return value.asOnly().asString();
        return model;
    }

    private boolean causedByTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof InterruptedIOException) return true;
            current = current.getCause();
        }
        return false;
    }

    private ApplicationException providerUnavailable() {
        return new ApplicationException("AI_REPORT_PROVIDER_UNAVAILABLE",
                HttpStatus.SERVICE_UNAVAILABLE, "AI 리포트 제공자에 연결하지 못했습니다.");
    }

    private ApplicationException invalid(String code) {
        return new ApplicationException(code, HttpStatus.BAD_GATEWAY,
                "AI 리포트 응답을 확인하지 못했습니다.");
    }
}
