package com.teamproject.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class ProductionConfigurationValidatorTest {

    @Test
    void acceptsCompleteProductionConfiguration() {
        MockEnvironment environment = productionEnvironment();

        assertThatCode(() -> new ProductionConfigurationValidator(environment).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsProductionWithoutOperationalMail() {
        MockEnvironment environment = productionEnvironment()
                .withProperty("app.mail.enabled", "false");

        assertThatThrownBy(() -> new ProductionConfigurationValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_ENABLED must be true");
    }

    @Test
    void rejectsUnsupportedDeploymentEnvironmentInsteadOfSkippingSafetyChecks() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.environment", "staging");

        assertThatThrownBy(() -> new ProductionConfigurationValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported APP_ENVIRONMENT");
    }

    private MockEnvironment productionEnvironment() {
        return new MockEnvironment()
                .withProperty("app.environment", "production")
                .withProperty("app.frontend-url", "https://totaskflow.com")
                .withProperty("app.jwt.secret", "production-secret-with-more-than-thirty-two-characters")
                .withProperty("app.jwt.secure-cookie", "true")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.datasource.url",
                        "jdbc:mysql://mysql:3306/totaskflow?sslMode=DISABLED")
                .withProperty("app.storage.provider", "local")
                .withProperty("app.storage.local-root", "/var/lib/totaskflow/uploads")
                .withProperty("app.demo.enabled", "false")
                .withProperty("spring.security.oauth2.client.registration.google.client-id", "google-client-id")
                .withProperty("spring.security.oauth2.client.registration.google.client-secret", "google-client-secret")
                .withProperty("app.toss.test-mode", "true")
                .withProperty("app.subscription.live-billing-enabled", "false")
                .withProperty("app.toss.client-key", "test_ck_example")
                .withProperty("app.toss.secret-key", "test_sk_example")
                .withProperty("app.toss.encryption-key-base64",
                        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
                .withProperty("app.mail.enabled", "true")
                .withProperty("spring.mail.host", "smtp.example.com")
                .withProperty("spring.mail.username", "mailer")
                .withProperty("spring.mail.password", "mail-secret")
                .withProperty("app.mail.from", "no-reply@totaskflow.com");
    }
}
