package com.teamproject.ai.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class AiAgentProperties {
    private final boolean enabled;
    private final String baseUrl;
    private final String internalSecret;
    private final Duration requestTimeout;

    public AiAgentProperties(
            @Value("${app.ai-agent.enabled:false}") boolean enabled,
            @Value("${app.ai-agent.base-url:http://localhost:8090}") String baseUrl,
            @Value("${app.ai-agent.internal-secret:}") String internalSecret,
            @Value("${app.ai-agent.request-timeout:60s}") Duration requestTimeout) {
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.internalSecret = internalSecret == null ? "" : internalSecret;
        this.requestTimeout = requestTimeout;
    }

    public boolean enabled() { return enabled; }
    public String baseUrl() { return baseUrl; }
    public String internalSecret() { return internalSecret; }
    public Duration requestTimeout() { return requestTimeout; }
    public boolean hasInternalSecret() { return !internalSecret.isBlank(); }
}
