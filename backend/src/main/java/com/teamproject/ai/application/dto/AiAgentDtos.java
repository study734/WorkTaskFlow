package com.teamproject.ai.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public final class AiAgentDtos {
    private AiAgentDtos() {}

    public record ChatRequest(
            @NotNull @Positive Long groupId,
            @NotBlank @Size(max = 2000) String message,
            @Size(max = 64) String threadId) {}

    public record ResumeRequest(
            @NotNull @Positive Long groupId,
            @NotBlank @Size(max = 64) String threadId,
            @NotNull Boolean approved,
            @Size(max = 500) String note) {}

    /**
     * status 는 completed 또는 awaiting_approval 이다.
     * awaiting_approval 이면 pending 에 승인 대기 중인 작업이 담긴다.
     */
    public record TurnResponse(
            String threadId,
            String status,
            String reply,
            Map<String, Object> pending) {}

    public record IndexResponse(
            int indexed, int skipped, int removed, int unsupported, List<String> failures) {}

    public record AgentHealthResponse(String status, boolean enabled, List<String> missing) {}
}
