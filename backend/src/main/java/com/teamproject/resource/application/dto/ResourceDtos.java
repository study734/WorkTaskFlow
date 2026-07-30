package com.teamproject.resource.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class ResourceDtos {
    private ResourceDtos() {}
    public record CreateLinkRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 1000) String url,
            Long taskId) {}
    public record ResourceResponse(Long id, Long groupId, Long taskId, String type, String title,
            String url, String originalFilename, String contentType, Long sizeBytes,
            Long createdByMemberId, String createdByNickname, LocalDateTime createdAt,
            boolean canDelete) {}
}
