package com.teamproject.authentication.infrastructure.web;

import com.teamproject.authentication.domain.token.SessionDevice;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class SessionDeviceResolver {
    public SessionDevice resolve(HttpServletRequest request) {
        String userAgent = clean(request.getHeader("User-Agent"), 500, "unknown");
        String suppliedName = clean(request.getHeader("X-Device-Name"), 100, "");
        return new SessionDevice(
                clean(request.getHeader("X-Device-Id"), 64, "unknown"),
                suppliedName.isBlank() ? deviceName(userAgent) : suppliedName,
                userAgent,
                clean(request.getRemoteAddr(), 64, "unknown"));
    }

    private String deviceName(String userAgent) {
        if (userAgent.contains("iPhone") || userAgent.contains("iPad")) return "Apple 모바일 기기";
        if (userAgent.contains("Android")) return "Android 기기";
        if (userAgent.contains("Windows")) return "Windows 기기";
        if (userAgent.contains("Mac OS")) return "Mac 기기";
        if (userAgent.contains("Linux")) return "Linux 기기";
        return "웹 브라우저";
    }

    private String clean(String value, int maxLength, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String cleaned = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return cleaned.substring(0, Math.min(cleaned.length(), maxLength));
    }
}
