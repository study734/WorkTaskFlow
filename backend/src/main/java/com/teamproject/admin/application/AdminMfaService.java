package com.teamproject.admin.application;

import com.teamproject.admin.domain.*;
import com.teamproject.admin.security.*;
import com.teamproject.authentication.domain.token.RefreshToken;
import com.teamproject.authentication.domain.token.RefreshTokenRepository;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.user.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service
public class AdminMfaService {
    private final AdminMfaCredentialRepository credentials;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AdminMfaCipher cipher;
    private final TotpService totp;
    private final SecureRandom random = new SecureRandom();
    public AdminMfaService(AdminMfaCredentialRepository credentials, UserRepository users,
            RefreshTokenRepository refreshTokens, AdminMfaCipher cipher, TotpService totp) {
        this.credentials = credentials; this.users = users; this.refreshTokens = refreshTokens;
        this.cipher = cipher; this.totp = totp;
    }
    @Transactional(readOnly = true)
    public Status status(Long userId, boolean sessionVerified) {
        User user = admin(userId);
        var value = credentials.findByUserId(userId);
        return new Status(value.filter(AdminMfaCredential::isEnabled).isPresent(), sessionVerified,
                cipher.configured(), value.map(AdminMfaCredential::getEnabledAt).orElse(null));
    }
    @Transactional
    public Setup setup(Long userId) {
        User user = admin(userId);
        if (!cipher.configured()) throw unavailable();
        String secret = totp.secret();
        AdminMfaCredential value = credentials.findByUserId(userId).orElse(null);
        if (value != null && value.isEnabled()) {
            throw new ApplicationException("ADMIN_MFA_ALREADY_ENABLED", HttpStatus.CONFLICT, "관리자 MFA가 이미 활성화되어 있습니다.");
        }
        if (value == null) credentials.save(new AdminMfaCredential(user, cipher.encrypt(secret)));
        else value.replacePendingSecret(cipher.encrypt(secret));
        String issuer = "toesa";
        String label = issuer + ":" + user.getUsername();
        String uri = "otpauth://totp/" + url(label) + "?secret=" + secret + "&issuer=" + url(issuer)
                + "&algorithm=SHA1&digits=6&period=30";
        return new Setup(secret, uri);
    }
    @Transactional
    public RecoveryCodes confirm(Long userId, String code) {
        User user = admin(userId);
        AdminMfaCredential value = credentials.findByUserId(userId).orElseThrow(() ->
                new ApplicationException("ADMIN_MFA_SETUP_REQUIRED", HttpStatus.CONFLICT, "관리자 MFA 설정을 먼저 시작해 주세요."));
        if (value.isEnabled()) throw new ApplicationException("ADMIN_MFA_ALREADY_ENABLED", HttpStatus.CONFLICT, "관리자 MFA가 이미 활성화되어 있습니다.");
        if (!totp.verify(cipher.decrypt(value.getEncryptedSecret()), code)) throw invalid();
        List<String> rawCodes = recoveryCodes();
        value.enable(rawCodes.stream().map(this::hash).collect(java.util.stream.Collectors.joining(",")));
        refreshTokens.findAllByUserId(userId).forEach(RefreshToken::revoke);
        user.invalidateSessions();
        return new RecoveryCodes(rawCodes);
    }
    @Transactional
    public boolean verifyForLogin(User user, String code) {
        if (user.getSystemRole() != User.SystemRole.ADMIN) return false;
        AdminMfaCredential value = credentials.findByUserId(user.getId()).orElse(null);
        if (value == null || !value.isEnabled()) return false;
        if (totp.verify(cipher.decrypt(value.getEncryptedSecret()), code)) return true;
        if (consumeRecoveryCode(value, code)) return true;
        throw new ApplicationException("ADMIN_MFA_REQUIRED", HttpStatus.UNAUTHORIZED,
                "관리자 인증 앱 코드 또는 복구 코드를 입력해 주세요.");
    }
    private boolean consumeRecoveryCode(AdminMfaCredential credential, String raw) {
        if (raw == null || credential.getRecoveryCodeHashes() == null) return false;
        String candidate = hash(raw.trim().toUpperCase(Locale.ROOT));
        List<String> hashes = new ArrayList<>(Arrays.asList(credential.getRecoveryCodeHashes().split(",")));
        boolean removed = hashes.removeIf(value -> MessageDigest.isEqual(
                value.getBytes(StandardCharsets.US_ASCII), candidate.getBytes(StandardCharsets.US_ASCII)));
        if (removed) credential.consumeRecoveryCodes(String.join(",", hashes));
        return removed;
    }
    private List<String> recoveryCodes() {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            byte[] bytes = new byte[6]; random.nextBytes(bytes);
            String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).toUpperCase(Locale.ROOT);
            result.add(value.substring(0, 4) + "-" + value.substring(4, 8));
        }
        return result;
    }
    private String hash(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.replace("-", "").getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private User admin(Long id) {
        return users.findById(id).filter(value -> value.getSystemRole() == User.SystemRole.ADMIN)
                .orElseThrow(() -> new ApplicationException("ADMIN_REQUIRED", HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."));
    }
    private String url(String value) { return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private ApplicationException invalid() {
        return new ApplicationException("ADMIN_MFA_CODE_INVALID", HttpStatus.UNAUTHORIZED, "관리자 MFA 코드가 올바르지 않습니다.");
    }
    private ApplicationException unavailable() {
        return new ApplicationException("ADMIN_MFA_ENCRYPTION_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
                "관리자 MFA 암호화 키가 설정되지 않았습니다.");
    }
    public record Status(boolean enabled, boolean sessionVerified, boolean encryptionConfigured,
            java.time.LocalDateTime enabledAt) {}
    public record Setup(String secret, String otpauthUri) {}
    public record RecoveryCodes(List<String> recoveryCodes) {}
}
