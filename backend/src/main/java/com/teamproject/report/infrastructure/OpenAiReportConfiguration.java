package com.teamproject.report.infrastructure;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class OpenAiReportConfiguration {
    @Bean("openAiReportClient")
    OpenAIClient openAiReportClient(
            @Value("${app.ai-report.api-key:}") String apiKey,
            @Value("${app.ai-report.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.ai-report.request-timeout:45s}") Duration timeout) {
        return OpenAIOkHttpClient.builder()
                .apiKey(apiKey.isBlank() ? "not-configured" : apiKey)
                .baseUrl(baseUrl)
                .timeout(timeout)
                .maxRetries(0)
                .build();
    }
}
