package com.teamproject.authentication.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class SessionDtos {
    private SessionDtos() {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password,
            @Size(max = 40) String mfaCode) {
        public LoginRequest(String username, String password) { this(username, password, null); }
        @Override public String toString() { return "LoginRequest[redacted]"; }
    }
    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
        @Override public String toString() {
            return "TokenResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
        }
    }
    public record MeResponse(Long userId, String username, String email, String name, String role) {
        @Override public String toString() { return "MeResponse[userId=" + userId + ", role=" + role + "]"; }
    }
    public record DeviceSessionResponse(String sessionId, String deviceName, String clientMode,
            String ipAddress, LocalDateTime createdAt, LocalDateTime lastUsedAt,
            LocalDateTime expiresAt, boolean current) {}
    public record DeviceSessionsResponse(List<DeviceSessionResponse> sessions) {}
}
