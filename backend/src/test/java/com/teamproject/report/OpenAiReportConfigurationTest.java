package com.teamproject.report;

import com.openai.client.OpenAIClient;
import com.teamproject.report.infrastructure.openai.OpenAIConfiguration;
import com.teamproject.report.infrastructure.openai.OpenAiReportProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SDK 클라이언트 설정이 v7-2 실행 정책을 지키는지 확인한다. 실제 OpenAI API는 호출하지
 * 않으며 네트워크도 쓰지 않는다. Bean이 만들어지는지와 설정 바인딩만 검증한다.
 *
 * <p>핵심은 <b>API 키가 없어도 context가 기동해야 한다</b>는 것이다. SDK 예제의
 * {@code fromEnv()}를 쓰면 키가 없을 때 Bean 생성이 실패해 로컬·CI에서 애플리케이션이
 * 아예 뜨지 않는다.
 */
class OpenAiReportConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of())
            .withUserConfiguration(OpenAIConfiguration.class);

    @Test
    @DisplayName("API 키가 없어도 context가 기동하고 openAiReportClient Bean이 하나 만들어진다")
    void startsWithoutApiKey() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OpenAIClient.class);
            assertThat(context.getBeanNamesForType(OpenAIClient.class))
                    .containsExactly("openAiReportClient");
        });
    }

    @Test
    @DisplayName("기존 app.ai-report.* 키와 환경변수 이름을 그대로 바인딩한다")
    void bindsExistingPropertyNames() {
        runner.withPropertyValues(
                        "app.ai-report.enabled=true",
                        "app.ai-report.api-key=test-key",
                        "app.ai-report.model=gpt-5.6-luna",
                        "app.ai-report.request-timeout=45s",
                        "app.ai-report.max-retries=1")
                .run(context -> {
                    OpenAiReportProperties properties =
                            context.getBean(OpenAiReportProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.apiKey()).isEqualTo("test-key");
                    assertThat(properties.model()).isEqualTo("gpt-5.6-luna");
                    assertThat(properties.requestTimeout()).isEqualTo(Duration.ofSeconds(45));
                    assertThat(properties.maxRetries()).isEqualTo(1);
                    assertThat(properties.hasApiKey()).isTrue();
                    assertThat(properties.hasModel()).isTrue();
                });
    }

    @Test
    @DisplayName("설정이 없으면 timeout 45초, maxRetries 1, 공식 base URL을 기본값으로 쓴다")
    void appliesExecutionPolicyDefaults() {
        runner.run(context -> {
            OpenAiReportProperties properties = context.getBean(OpenAiReportProperties.class);
            assertThat(properties.requestTimeout()).isEqualTo(Duration.ofSeconds(45));
            assertThat(properties.maxRetries()).isEqualTo(1);
            assertThat(properties.baseUrl()).isEqualTo("https://api.openai.com/v1");
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.hasApiKey()).isFalse();
            assertThat(properties.hasModel()).isFalse();
        });
    }

    /** 빈 문자열과 공백은 "미설정"과 같게 다뤄야 adapter의 미설정 판정이 일관된다. */
    @Test
    @DisplayName("공백만 있는 키와 모델은 미설정으로 정규화한다")
    void normalizesBlankApiKeyAndModel() {
        runner.withPropertyValues(
                        "app.ai-report.api-key=   ",
                        "app.ai-report.model=")
                .run(context -> {
                    OpenAiReportProperties properties =
                            context.getBean(OpenAiReportProperties.class);
                    assertThat(properties.apiKey()).isEmpty();
                    assertThat(properties.model()).isEmpty();
                    assertThat(properties.hasApiKey()).isFalse();
                    assertThat(properties.hasModel()).isFalse();
                    assertThat(context).hasSingleBean(OpenAIClient.class);
                });
    }

    @Test
    @DisplayName("명시적으로 지정한 재시도 횟수 0은 기본값으로 덮어쓰지 않는다")
    void keepsExplicitZeroRetries() {
        runner.withPropertyValues("app.ai-report.max-retries=0")
                .run(context -> assertThat(
                        context.getBean(OpenAiReportProperties.class).maxRetries()).isZero());
    }

    @Test
    @DisplayName("허용 상한 3까지는 그대로 받는다")
    void acceptsRetryCountUpToUpperBound() {
        runner.withPropertyValues("app.ai-report.max-retries=3")
                .run(context -> assertThat(
                        context.getBean(OpenAiReportProperties.class).maxRetries()).isEqualTo(3));
    }

    @Test
    @DisplayName("허용 범위 0~3을 벗어난 재시도 설정은 기동 시점에 거부한다")
    void rejectsRetryCountOutsideAllowedRange() {
        runner.withPropertyValues("app.ai-report.max-retries=4")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseInstanceOf(IllegalArgumentException.class));

        runner.withPropertyValues("app.ai-report.max-retries=-1")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseInstanceOf(IllegalArgumentException.class));
    }

    @Test
    @DisplayName("0 이하 timeout은 기동 시점에 거부한다")
    void rejectsNonPositiveTimeout() {
        runner.withPropertyValues("app.ai-report.request-timeout=0s")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseInstanceOf(IllegalArgumentException.class));
    }

    /** 바인딩 실패는 UnsatisfiedDependencyException에 감싸여 나오므로 원인 사슬로 확인한다. */
    @Test
    @DisplayName("타입이 맞지 않는 설정은 기동 시점에 바인딩 실패로 드러난다")
    void surfacesBindingFailures() {
        runner.withPropertyValues("app.ai-report.max-retries=not-a-number")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasCauseInstanceOf(ConfigurationPropertiesBindException.class)
                        .hasMessageContaining("OpenAiReportProperties"));
    }
}
