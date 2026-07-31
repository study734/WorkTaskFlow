package com.teamproject.report.infrastructure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OpenAI 리포트 연동 설정. prefix와 환경변수 이름은 기존 배포 환경을 그대로 유지한다
 * (`app.ai-report.*`, `AI_REPORT_ENABLED`, `OPENAI_API_KEY`, `OPENAI_MODEL`,
 * `OPENAI_REQUEST_TIMEOUT`). 이름을 바꾸면 compose 파일과 CI secret을 동시에 고쳐야 하고,
 * 누락되면 운영에서 조용히 fallback으로만 동작한다.
 *
 * <p>키가 비어 있어도 예외를 던지지 않는다. Spring context는 API 키 없이도 기동해야 하며,
 * 호출 시점에 adapter가 미설정을 판단한다.
 */
@ConfigurationProperties(prefix = "app.ai-report")
public record OpenAiReportProperties(
        boolean enabled,
        String apiKey,
        String model,
        String baseUrl,
        Duration requestTimeout,
        Integer maxRetries,
        Long maxOutputTokens,
        String promptVersion) {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(45);
    /** SDK 기본값은 2회다. 주간 리포트는 사용자가 기다리는 동기 호출이므로 1회로 제한한다. */
    public static final int DEFAULT_MAX_RETRIES = 1;
    public static final int MAX_ALLOWED_RETRIES = 3;
    public static final long DEFAULT_MAX_OUTPUT_TOKENS = 3000L;
    public static final String DEFAULT_PROMPT_VERSION = "v7-2-prompt-001";

    public OpenAiReportProperties {
        apiKey = blankToEmpty(apiKey);
        model = blankToEmpty(model);
        baseUrl = isBlank(baseUrl) ? DEFAULT_BASE_URL : baseUrl.trim();
        requestTimeout = requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
        maxRetries = maxRetries == null ? DEFAULT_MAX_RETRIES : maxRetries;
        maxOutputTokens = (maxOutputTokens == null || maxOutputTokens <= 0) ? DEFAULT_MAX_OUTPUT_TOKENS : maxOutputTokens;
        promptVersion = isBlank(promptVersion) ? DEFAULT_PROMPT_VERSION : promptVersion.trim();

        if (requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "app.ai-report.request-timeout must be positive");
        }
        if (maxRetries < 0 || maxRetries > MAX_ALLOWED_RETRIES) {
            throw new IllegalArgumentException(
                    "app.ai-report.max-retries must be between 0 and " + MAX_ALLOWED_RETRIES);
        }
    }

    /** API 키가 없어도 client Bean은 만들어져야 한다. 호출은 adapter가 막는다. */
    public boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    public boolean hasModel() {
        return !model.isEmpty();
    }

    private static String blankToEmpty(String value) {
        return isBlank(value) ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
