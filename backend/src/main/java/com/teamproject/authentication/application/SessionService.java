package com.teamproject.authentication.application;

import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SessionDtos.MeResponse;
import com.teamproject.authentication.application.token.RefreshTokenService;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.teamproject.authentication.domain.token.RefreshToken.ClientMode;
import com.teamproject.authentication.domain.token.SessionDevice;
import com.teamproject.authentication.application.dto.SessionDtos.DeviceSessionsResponse;
import com.teamproject.notification.application.NotificationService;
import com.teamproject.admin.application.AdminMfaService;

@Service
public class SessionService {
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY_AUDIT");
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final AccessSessionIssuer issuer;
    private final NotificationService notifications;
    private final boolean demoEnabled;
    private final String demoUsername;
    private final AdminMfaService adminMfa;

    public SessionService(UserRepository users, PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokens, AccessSessionIssuer issuer, NotificationService notifications,
            AdminMfaService adminMfa,
            @Value("${app.demo.enabled:true}") boolean demoEnabled,
            @Value("${app.demo.username:demo_leader}") String demoUsername) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.issuer = issuer;
        this.notifications = notifications;
        this.adminMfa = adminMfa;
        this.demoEnabled = demoEnabled;
        this.demoUsername = demoUsername;
    }

    @Transactional
    public IssuedTokens login(LoginRequest request) {
        return login(request, ClientMode.WEB);
    }

    @Transactional
    public IssuedTokens login(LoginRequest request, ClientMode mode) {
        return login(request, mode, SessionDevice.unknown());
    }

    @Transactional
    public IssuedTokens login(LoginRequest request, ClientMode mode, SessionDevice device) {
        User user = users.findByUsernameIgnoreCase(request.username().trim()).orElseThrow(this::credentials);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw credentials();
        }
        boolean mfaVerified = adminMfa.verifyForLogin(user, request.mfaCode());
        boolean newDevice = refreshTokens.hasAnySession(user.getId())
                && !refreshTokens.isKnownDevice(user.getId(), device.deviceId());
        user.recordLogin();
        securityLog.info("event=LOGIN outcome=SUCCESS actorUserId={}", user.getId());
        IssuedTokens tokens = issuer.issue(user, mode, device, mfaVerified);
        if (newDevice) {
            notifications.newDeviceLogin(user, device.deviceName(),
                    "SECURITY_NEW_DEVICE:" + device.deviceId() + ":" + user.getAuthVersion());
        }
        return tokens;
    }

    @Transactional
    public IssuedTokens demo() {
        if (!demoEnabled) {
            throw new ApplicationException("DEMO_DISABLED", HttpStatus.SERVICE_UNAVAILABLE,
                    "현재 데모 체험을 이용할 수 없습니다.");
        }
        User user = users.findByUsernameIgnoreCase(demoUsername).filter(User::isActive).orElseThrow(() ->
                new ApplicationException("DEMO_NOT_READY", HttpStatus.SERVICE_UNAVAILABLE,
                        "데모 데이터가 아직 준비되지 않았습니다."));
        user.recordLogin();
        securityLog.info("event=DEMO_LOGIN outcome=SUCCESS actorUserId={}", user.getId());
        return issuer.issue(user);
    }

    @Transactional(noRollbackFor = ApplicationException.class)
    public IssuedTokens refresh(String raw) { return issuer.refresh(raw); }

    @Transactional(noRollbackFor = ApplicationException.class)
    public IssuedTokens refresh(String raw, ClientMode requestedMode) { return issuer.refresh(raw, requestedMode); }

    @Transactional(noRollbackFor = ApplicationException.class)
    public IssuedTokens refresh(String raw, ClientMode requestedMode, SessionDevice device) {
        return issuer.refresh(raw, requestedMode, device);
    }

    @Transactional
    public void logout(String raw) { refreshTokens.revoke(raw); }

    @Transactional
    public void logoutAll(Long userId) {
        User user = users.findById(userId).orElseThrow(() ->
                new ApplicationException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        refreshTokens.revokeAll(userId);
        user.invalidateSessions();
        securityLog.info("event=LOGOUT_ALL outcome=SUCCESS actorUserId={}", userId);
    }

    @Transactional(readOnly = true)
    public DeviceSessionsResponse sessions(Long userId, String currentRawToken) {
        var values = refreshTokens.sessions(userId, currentRawToken);
        boolean sharedDemo = users.findById(userId)
                .map(user -> user.getUsername().startsWith("demo_")).orElse(false);
        return new DeviceSessionsResponse(sharedDemo
                ? values.stream().filter(com.teamproject.authentication.application.dto.SessionDtos.DeviceSessionResponse::current).toList()
                : values);
    }

    @Transactional
    public boolean logoutSession(Long userId, String sessionId, String currentRawToken) {
        boolean current = sessionId.equals(refreshTokens.sessionId(currentRawToken));
        refreshTokens.revokeSession(userId, sessionId);
        securityLog.info("event=LOGOUT_DEVICE outcome=SUCCESS actorUserId={} sessionId={}", userId, sessionId);
        return current;
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long id) {
        User user = users.findById(id).orElseThrow(() ->
                new ApplicationException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        return new MeResponse(user.getId(), user.getUsername(), user.getEmail(), user.getName(),
                user.getSystemRole().name());
    }

    private ApplicationException credentials() {
        return new ApplicationException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED,
                "아이디 또는 비밀번호가 올바르지 않습니다.");
    }
}
