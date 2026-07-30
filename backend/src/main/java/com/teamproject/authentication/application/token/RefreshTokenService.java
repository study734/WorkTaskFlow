package com.teamproject.authentication.application.token;

import com.teamproject.authentication.domain.token.RefreshToken;
import com.teamproject.authentication.domain.token.RefreshTokenRepository;
import com.teamproject.authentication.infrastructure.crypto.HashService;
import com.teamproject.authentication.domain.token.SessionDevice;
import com.teamproject.authentication.application.dto.SessionDtos.DeviceSessionResponse;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.notification.application.NotificationService;
import com.teamproject.user.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.List;

import com.teamproject.authentication.domain.token.RefreshToken.ClientMode;

@Service
public class RefreshTokenService {
    private final SecureRandom random = new SecureRandom();
    private final RefreshTokenRepository repository;
    private final HashService hashService;
    private final NotificationService notifications;
    private final long webRefreshSeconds;
    private final long pwaIdleSeconds;
    private final long pwaAbsoluteSeconds;
    public RefreshTokenService(RefreshTokenRepository repository, HashService hashService,
            NotificationService notifications,
            @Value("${app.jwt.refresh-seconds}") long webRefreshSeconds,
            @Value("${app.jwt.pwa-refresh-idle-seconds:2592000}") long pwaIdleSeconds,
            @Value("${app.jwt.pwa-refresh-absolute-seconds:7776000}") long pwaAbsoluteSeconds) {
        this.repository = repository;
        this.hashService = hashService;
        this.notifications = notifications;
        this.webRefreshSeconds = webRefreshSeconds;
        this.pwaIdleSeconds = pwaIdleSeconds;
        this.pwaAbsoluteSeconds = pwaAbsoluteSeconds;
    }

    @Transactional
    public IssuedRefreshToken issue(User user, ClientMode mode) {
        return issue(user, mode, SessionDevice.unknown());
    }

    @Transactional
    public IssuedRefreshToken issue(User user, ClientMode mode, SessionDevice device) {
        LocalDateTime now = LocalDateTime.now();
        long idleSeconds = idleSeconds(mode);
        LocalDateTime absoluteExpiry = now.plusSeconds(mode == ClientMode.PWA ? pwaAbsoluteSeconds : webRefreshSeconds);
        String sessionId = UUID.randomUUID().toString();
        return save(user, sessionId, mode, now.plusSeconds(idleSeconds), absoluteExpiry,
                device, now, now);
    }

    @Transactional(noRollbackFor = ApplicationException.class)
    public RotatedRefreshToken rotate(String raw) { return rotate(raw, null); }

    @Transactional(noRollbackFor = ApplicationException.class)
    public RotatedRefreshToken rotate(String raw, ClientMode requestedMode) {
        return rotate(raw, requestedMode, null);
    }

    @Transactional(noRollbackFor = ApplicationException.class)
    public RotatedRefreshToken rotate(String raw, ClientMode requestedMode, SessionDevice currentDevice) {
        RefreshToken token = findLocked(raw);
        LocalDateTime now = LocalDateTime.now();
        if (token.isRevoked()) {
            revokeSession(token.getSessionId());
            if (token.getUser().isActive()) {
                notifications.refreshTokenReused(token.getUser(), token.getDeviceName(),
                        "SECURITY_SESSION_REUSED:" + token.getSessionId());
            }
            throw reused();
        }
        if (!token.isValid(now)) {
            token.revoke();
            throw invalid();
        }
        token.revoke();
        ClientMode mode = requestedMode == ClientMode.PWA ? ClientMode.PWA : token.getClientMode();
        LocalDateTime absoluteExpiry = token.getClientMode() == ClientMode.WEB && mode == ClientMode.PWA
                ? now.plusSeconds(pwaAbsoluteSeconds) : token.getAbsoluteExpiresAt();
        LocalDateTime slidingExpiry = now.plusSeconds(idleSeconds(mode));
        LocalDateTime expiry = slidingExpiry.isBefore(absoluteExpiry) ? slidingExpiry : absoluteExpiry;
        boolean newlyIdentifiedDevice = currentDevice != null
                && !"unknown".equals(currentDevice.deviceId())
                && !repository.existsByUserIdAndDeviceId(token.getUser().getId(), currentDevice.deviceId())
                && repository.existsByUserIdAndDeviceIdNot(token.getUser().getId(), "unknown");
        SessionDevice device = currentDevice == null ? new SessionDevice(
                token.getDeviceId(), token.getDeviceName(), token.getUserAgent(), token.getIpAddress()) : currentDevice;
        IssuedRefreshToken issued = save(token.getUser(), token.getSessionId(), mode,
                expiry, absoluteExpiry, device, token.getCreatedAt(), now);
        if (newlyIdentifiedDevice) {
            notifications.newDeviceLogin(token.getUser(), device.deviceName(),
                    "SECURITY_NEW_DEVICE:" + device.deviceId() + ":" + token.getUser().getAuthVersion());
        }
        return new RotatedRefreshToken(token.getUser(), issued);
    }

    private IssuedRefreshToken save(User user, String sessionId, ClientMode mode, LocalDateTime expiry,
            LocalDateTime absoluteExpiry, SessionDevice device, LocalDateTime createdAt, LocalDateTime now) {
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(random.generateSeed(48));
        repository.save(new RefreshToken(user, hashService.sha256(raw), sessionId, mode, expiry, absoluteExpiry,
                device, createdAt, now));
        return new IssuedRefreshToken(raw, sessionId, Math.max(1, Duration.between(now, expiry).toSeconds()));
    }

    @Transactional public void revoke(String raw) { if (raw != null) repository.findByTokenHash(hashService.sha256(raw)).ifPresent(RefreshToken::revoke); }
    @Transactional public void revokeAll(Long userId) { repository.findAllByUserId(userId).forEach(RefreshToken::revoke); }
    @Transactional
    public void revokeSession(Long userId, String sessionId) {
        List<RefreshToken> family = repository.findAllBySessionId(sessionId);
        if (family.isEmpty() || family.stream().anyMatch(token -> !token.getUser().getId().equals(userId))) {
            throw new ApplicationException("SESSION_NOT_FOUND", HttpStatus.NOT_FOUND, "로그인 세션을 찾을 수 없습니다.");
        }
        family.forEach(RefreshToken::revoke);
    }
    @Transactional(readOnly = true)
    public List<DeviceSessionResponse> sessions(Long userId, String currentRawToken) {
        String currentSessionId = sessionId(currentRawToken);
        LocalDateTime now = LocalDateTime.now();
        return repository.findActiveByUserId(userId, now).stream()
                .map(token -> new DeviceSessionResponse(token.getSessionId(), token.getDeviceName(),
                        token.getClientMode().name(), token.getIpAddress(), token.getCreatedAt(),
                        token.getLastUsedAt(), token.getExpiresAt(), token.getSessionId().equals(currentSessionId)))
                .toList();
    }
    @Transactional(readOnly = true)
    public boolean isKnownDevice(Long userId, String deviceId) {
        return repository.existsByUserIdAndDeviceId(userId, deviceId);
    }
    @Transactional(readOnly = true)
    public boolean hasAnySession(Long userId) { return repository.existsByUserId(userId); }
    @Transactional(readOnly = true)
    public String sessionId(String raw) {
        if (raw == null || raw.isBlank()) return "";
        return repository.findByTokenHash(hashService.sha256(raw)).map(RefreshToken::getSessionId).orElse("");
    }
    private void revokeSession(String sessionId) { repository.findAllBySessionId(sessionId).forEach(RefreshToken::revoke); }
    private RefreshToken findLocked(String raw) {
        if (raw == null || raw.isBlank()) throw invalid();
        return repository.findLockedByTokenHash(hashService.sha256(raw)).orElseThrow(this::invalid);
    }
    private long idleSeconds(ClientMode mode) { return mode == ClientMode.PWA ? pwaIdleSeconds : webRefreshSeconds; }
    private ApplicationException invalid() { return new ApplicationException("REFRESH_TOKEN_INVALID", HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다."); }
    private ApplicationException reused() { return new ApplicationException("REFRESH_TOKEN_REUSED", HttpStatus.UNAUTHORIZED, "세션 보안을 위해 이 기기의 로그인이 해제되었습니다."); }
    public record IssuedRefreshToken(String rawToken, String sessionId, long cookieMaxAgeSeconds) {}
    public record RotatedRefreshToken(User user, IssuedRefreshToken refreshToken) {}
}
