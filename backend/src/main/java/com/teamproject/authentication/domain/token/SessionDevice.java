package com.teamproject.authentication.domain.token;

public record SessionDevice(String deviceId, String deviceName, String userAgent, String ipAddress) {
    public static SessionDevice unknown() {
        return new SessionDevice("unknown", "알 수 없는 기기", "unknown", "unknown");
    }
}
