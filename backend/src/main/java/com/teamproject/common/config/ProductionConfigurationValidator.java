package com.teamproject.common.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Component
public class ProductionConfigurationValidator {
    private final Environment environment;

    public ProductionConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        String stage = value("app.environment").toLowerCase(Locale.ROOT);
        if (stage.equals("local") || stage.equals("test")) return;
        if (!stage.equals("production")) {
            throw new IllegalStateException(
                    "Unsupported APP_ENVIRONMENT: use production for deployments and local/test for development");
        }

        List<String> failures = new ArrayList<>();
        require("app.frontend-url", value -> value.startsWith("https://"), failures,
                "FRONTEND_URL must use HTTPS");
        require("app.jwt.secret", value -> value.length() >= 32 && !value.contains("change-me"), failures,
                "JWT_SECRET must be an independent secret of at least 32 characters");
        require("app.jwt.secure-cookie", Boolean::parseBoolean, failures,
                "AUTH_SECURE_COOKIE must be true");
        require("spring.jpa.hibernate.ddl-auto", value -> value.equalsIgnoreCase("validate"), failures,
                "SPRING_JPA_HIBERNATE_DDL_AUTO must be validate");
        require("spring.datasource.url",
                value -> value.startsWith("jdbc:mysql://mysql:3306/")
                        && value.contains("sslMode=DISABLED"),
                failures, "SPRING_DATASOURCE_URL must use the internal mysql service");
        require("app.storage.provider", value -> value.equalsIgnoreCase("local"), failures,
                "STORAGE_PROVIDER must be local");
        require("app.storage.local-root", value -> !value.isBlank(), failures,
                "UPLOAD_LOCAL_ROOT is required");
        require("app.demo.enabled", value -> !Boolean.parseBoolean(value), failures,
                "DEMO_ENABLED must be false");
        require("spring.security.oauth2.client.registration.google.client-id",
                value -> !value.isBlank() && !value.equalsIgnoreCase("disabled"), failures,
                "OAUTH2_GOOGLE_CLIENT_ID is required");
        require("spring.security.oauth2.client.registration.google.client-secret",
                value -> !value.isBlank() && !value.equalsIgnoreCase("disabled"), failures,
                "OAUTH2_GOOGLE_CLIENT_SECRET is required");
        require("app.toss.test-mode", Boolean::parseBoolean, failures,
                "TOSS_TEST_MODE must remain true");
        require("app.subscription.live-billing-enabled", value -> !Boolean.parseBoolean(value), failures,
                "SUBSCRIPTION_LIVE_BILLING_ENABLED must remain false");
        require("app.toss.client-key",
                value -> value.startsWith("test_ck_") || value.startsWith("test_gck_"), failures,
                "TOSS_CLIENT_KEY must be an official test key");
        require("app.toss.secret-key",
                value -> value.startsWith("test_sk_") || value.startsWith("test_gsk_"), failures,
                "TOSS_SECRET_KEY must be an official test key");
        require("app.toss.encryption-key-base64", this::isExact32ByteBase64Key, failures,
                "PAYMENT_ENCRYPTION_KEY_BASE64 must decode to exactly 32 bytes");

        boolean mailEnabled = Boolean.parseBoolean(value("app.mail.enabled"));
        if (stage.equals("production") && !mailEnabled) {
            failures.add("MAIL_ENABLED must be true for production signup and recovery flows");
        }
        if (mailEnabled) {
            require("spring.mail.host", value -> !value.isBlank(), failures, "MAIL_HOST is required");
            require("spring.mail.username", value -> !value.isBlank(), failures, "MAIL_USERNAME is required");
            require("spring.mail.password", value -> !value.isBlank(), failures, "MAIL_PASSWORD is required");
            require("app.mail.from", value -> value.contains("@") && !value.endsWith(".local"), failures,
                    "MAIL_FROM must be a verified non-local sender");
        }

        if (Boolean.parseBoolean(value("app.admin.enabled"))) {
            require("app.admin.frontend-url", value -> value.startsWith("https://"), failures,
                    "ADMIN_FRONTEND_URL must use HTTPS");
            require("app.admin.allowed-ips",
                    value -> !value.isBlank() && !value.contains("0.0.0.0/0") && !value.contains("::/0"),
                    failures, "ADMIN_ALLOWED_IPS must contain a restricted allow-list");
            require("app.admin.mfa-encryption-key-base64", this::isBase64Key, failures,
                    "ADMIN_MFA_ENCRYPTION_KEY_BASE64 must decode to at least 32 bytes");
        }

        if (!failures.isEmpty()) {
            throw new IllegalStateException("Unsafe " + stage + " configuration: " + String.join("; ", failures));
        }
    }

    private void require(String property, java.util.function.Predicate<String> predicate,
            List<String> failures, String message) {
        if (!predicate.test(value(property))) failures.add(message);
    }

    private String value(String property) {
        return environment.getProperty(property, "").trim();
    }

    private boolean isBase64Key(String value) {
        try {
            return Base64.getDecoder().decode(value).length >= 32;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isExact32ByteBase64Key(String value) {
        try {
            return Base64.getDecoder().decode(value).length == 32;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
