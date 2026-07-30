package com.teamproject.authentication.domain.token;

import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_refresh_session", columnList = "session_id")
})
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;
    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;
    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;
    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;
    @Column(name = "user_agent", nullable = false, length = 500)
    private String userAgent;
    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;
    @Enumerated(EnumType.STRING)
    @Column(name = "client_mode", nullable = false, length = 10)
    private ClientMode clientMode;
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    @Column(nullable = false)
    private LocalDateTime absoluteExpiresAt;
    private LocalDateTime revokedAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime lastUsedAt;

    protected RefreshToken() {}
    public RefreshToken(User user, String tokenHash, String sessionId, ClientMode clientMode,
            LocalDateTime expiresAt, LocalDateTime absoluteExpiresAt, SessionDevice device,
            LocalDateTime createdAt, LocalDateTime lastUsedAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.sessionId = sessionId;
        this.deviceId = device.deviceId();
        this.deviceName = device.deviceName();
        this.userAgent = device.userAgent();
        this.ipAddress = device.ipAddress();
        this.clientMode = clientMode;
        this.expiresAt = expiresAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
    }
    public boolean isValid(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now) && absoluteExpiresAt.isAfter(now);
    }
    public void revoke() { if (revokedAt == null) revokedAt = LocalDateTime.now(); }
    public User getUser() { return user; }
    public String getSessionId() { return sessionId; }
    public ClientMode getClientMode() { return clientMode; }
    public LocalDateTime getAbsoluteExpiresAt() { return absoluteExpiresAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revokedAt != null; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public String getUserAgent() { return userAgent; }
    public String getIpAddress() { return ipAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }

    public enum ClientMode { WEB, PWA }
}
