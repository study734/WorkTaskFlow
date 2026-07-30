package com.teamproject.user.application;

import com.teamproject.authentication.domain.oauth.SocialAccountRepository;
import com.teamproject.authentication.domain.token.OneTimeTokenRepository;
import com.teamproject.authentication.domain.token.RefreshToken;
import com.teamproject.authentication.domain.token.RefreshTokenRepository;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.user.application.dto.UserAccountDtos.ChangePasswordRequest;
import com.teamproject.user.application.dto.UserAccountDtos.WithdrawRequest;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UserAccountService {
    private static final Duration SOCIAL_REAUTH_WINDOW = Duration.ofMinutes(5);
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final OneTimeTokenRepository oneTimeTokens;
    private final SocialAccountRepository socialAccounts;

    public UserAccountService(UserRepository users, PasswordEncoder passwordEncoder,
            RefreshTokenRepository refreshTokens, OneTimeTokenRepository oneTimeTokens,
            SocialAccountRepository socialAccounts) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.oneTimeTokens = oneTimeTokens;
        this.socialAccounts = socialAccounts;
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = activeUser(userId);
        if (user.getPasswordHash() == null) {
            throw new ApplicationException("PASSWORD_LOGIN_REQUIRED", HttpStatus.CONFLICT,
                    "비밀번호 로그인 계정에서만 비밀번호를 변경할 수 있습니다.");
        }
        verifyPassword(user, request.currentPassword());
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApplicationException("PASSWORD_UNCHANGED", HttpStatus.BAD_REQUEST,
                    "현재 비밀번호와 다른 비밀번호를 입력해 주세요.");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        user.invalidateSessions();
        revokeAllRefreshTokens(userId);
    }

    @Transactional
    public void withdraw(Long userId, WithdrawRequest request) {
        User user = activeUser(userId);
        if (user.getPasswordHash() != null) {
            verifyPassword(user, request.currentPassword());
        } else if (user.getLastLoginAt() == null ||
                user.getLastLoginAt().isBefore(LocalDateTime.now().minus(SOCIAL_REAUTH_WINDOW))) {
            throw new ApplicationException("SOCIAL_REAUTH_REQUIRED", HttpStatus.UNAUTHORIZED,
                    "소셜 로그인으로 다시 인증한 뒤 탈퇴해 주세요.");
        }

        String originalEmail = user.getEmail();
        revokeAllRefreshTokens(userId);
        oneTimeTokens.deleteAllByEmailIgnoreCase(originalEmail);
        socialAccounts.deleteAllByUserId(userId);
        String suffix = userId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        user.anonymizeAndWithdraw("withdrawn_" + suffix, "withdrawn_" + suffix + "@invalid.local");
    }

    private User activeUser(Long userId) {
        User user = users.findById(userId).orElseThrow(() ->
                new ApplicationException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (!user.isActive()) {
            throw new ApplicationException("ACCOUNT_INACTIVE", HttpStatus.FORBIDDEN, "사용할 수 없는 계정입니다.");
        }
        return user;
    }

    private void verifyPassword(User user, String rawPassword) {
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new ApplicationException("CURRENT_PASSWORD_INVALID", HttpStatus.BAD_REQUEST,
                    "현재 비밀번호가 올바르지 않습니다.");
        }
    }

    private void revokeAllRefreshTokens(Long userId) {
        refreshTokens.findAllByUserId(userId).forEach(RefreshToken::revoke);
    }
}
