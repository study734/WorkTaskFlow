package com.teamproject.authentication.application;

import com.teamproject.authentication.application.dto.RecoveryDtos.PasswordResetConfirmRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.authentication.application.token.RefreshTokenService;
import com.teamproject.authentication.infrastructure.mail.MailService;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class RecoveryService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final OneTimeTokenService oneTimeTokens;
    private final RefreshTokenService refreshTokens;
    private final MailService mail;
    private final String frontendUrl;

    public RecoveryService(UserRepository users, PasswordEncoder passwordEncoder,
            OneTimeTokenService oneTimeTokens, RefreshTokenService refreshTokens, MailService mail,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.oneTimeTokens = oneTimeTokens;
        this.refreshTokens = refreshTokens;
        this.mail = mail;
        this.frontendUrl = frontendUrl;
    }

    @Transactional(readOnly = true)
    public void remindUsername(String rawEmail) {
        users.findByEmailIgnoreCase(normalizeEmail(rawEmail)).ifPresent(user ->
                mail.sendBestEffort(user.getEmail(), "[퇴사] 아이디 안내",
                        "회원님의 아이디는 " + user.getUsername() + " 입니다."));
    }

    @Transactional
    public void requestPasswordReset(String rawEmail) {
        users.findByEmailIgnoreCase(normalizeEmail(rawEmail)).ifPresent(user -> {
            String token = oneTimeTokens.issueResetToken(user.getEmail());
            mail.sendBestEffort(user.getEmail(), "[퇴사] 비밀번호 재설정",
                    frontendUrl + "/reset-password?email=" + user.getEmail() + "&token=" + token);
        });
    }

    @Transactional
    public void resetPassword(PasswordResetConfirmRequest request) {
        String email = normalizeEmail(request.email());
        User user = users.findByEmailIgnoreCase(email).orElseThrow(() ->
                new ApplicationException("TOKEN_INVALID", HttpStatus.BAD_REQUEST, "재설정 정보가 올바르지 않습니다."));
        oneTimeTokens.consumeResetToken(email, request.token());
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        user.invalidateSessions();
        refreshTokens.revokeAll(user.getId());
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
