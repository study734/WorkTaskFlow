package com.teamproject.report.infrastructure.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 공식 OpenAI Java SDK 클라이언트를 애플리케이션당 하나의 singleton Bean으로 등록한다.
 * SDK 의존은 이 패키지 아래로 격리하고 application·domain 계층은 {@code com.openai.*}를
 * import하지 않는다.
 *
 * <p>{@code OpenAIOkHttpClient.builder().fromEnv()}는 쓰지 않는다. {@code fromEnv()}는
 * {@code OPENAI_API_KEY}가 없으면 Bean 생성 시점에 예외를 던져 Spring context 기동 자체를
 *막는다. API 키 없이도 기동해야 로컬·CI에서 나머지 흐름을 검증할 수 있으므로 명시적
 * {@code apiKey(...)}와 placeholder fallback을 쓴다. 미설정 상태의 호출 차단은 adapter가
 * 담당한다.
 *
 * <p>Bean 이름 {@code openAiReportClient}는 기존 adapter가
 * {@code @Qualifier}로 주입받고 있으므로 그대로 유지한다.
 */
@Configuration
@EnableConfigurationProperties(OpenAiReportProperties.class)
public class OpenAIConfiguration {

    /** 키가 없을 때 SDK builder를 통과시키기 위한 값. 실제 호출은 adapter가 먼저 막는다. */
    private static final String UNCONFIGURED_API_KEY = "not-configured";

    @Bean("openAiReportClient")
    OpenAIClient openAiReportClient(OpenAiReportProperties properties) {
        return OpenAIOkHttpClient.builder()
                .apiKey(properties.hasApiKey() ? properties.apiKey() : UNCONFIGURED_API_KEY)
                .baseUrl(properties.baseUrl())
                .timeout(properties.requestTimeout())
                .maxRetries(properties.maxRetries())
                .responseValidation(true)
                .build();
    }
}
