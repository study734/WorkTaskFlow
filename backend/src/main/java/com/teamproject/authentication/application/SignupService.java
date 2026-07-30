package com.teamproject.authentication.application;

import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupResponse;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.authentication.infrastructure.mail.MailService;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.PersonalGroupProvisioner;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import com.teamproject.user.domain.UserConsent;
import com.teamproject.user.domain.UserConsentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class SignupService {
    static final String CONSENT_POLICY_VERSION = "2026-07-25-v2";
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final OneTimeTokenService oneTimeTokens;
    private final MailService mail;
    private final PersonalGroupProvisioner personalGroups;
    private final UserConsentRepository consents;

    public SignupService(UserRepository users, PasswordEncoder passwordEncoder,
            OneTimeTokenService oneTimeTokens, MailService mail, PersonalGroupProvisioner personalGroups,
            UserConsentRepository consents) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.oneTimeTokens = oneTimeTokens;
        this.mail = mail;
        this.personalGroups = personalGroups;
        this.consents = consents;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = normalizeEmail(request.email());
        if (users.existsByUsernameIgnoreCase(username)) throw conflict("USERNAME_EXISTS", "이미 사용 중인 아이디입니다.");
        if (users.existsByEmailIgnoreCase(email)) throw conflict("EMAIL_EXISTS", "이미 가입된 이메일입니다.");
        oneTimeTokens.consumeCode(email, request.verificationCode());
        User user = users.save(new User(username, email, passwordEncoder.encode(request.password()), request.name().trim(), true));
        consents.save(new UserConsent(user, UserConsent.Type.TERMS, CONSENT_POLICY_VERSION, true, "SIGNUP"));
        consents.save(new UserConsent(user, UserConsent.Type.PRIVACY_COLLECTION, CONSENT_POLICY_VERSION, true, "SIGNUP"));
        consents.save(new UserConsent(user, UserConsent.Type.AGE_14_OR_OLDER, CONSENT_POLICY_VERSION, true, "SIGNUP"));
        consents.save(new UserConsent(user, UserConsent.Type.SERVICE_NOTIFICATIONS, CONSENT_POLICY_VERSION,
                request.notificationAgreed(), "SIGNUP"));
        consents.save(new UserConsent(user, UserConsent.Type.MARKETING_MESSAGES, CONSENT_POLICY_VERSION,
                request.marketingAgreed(), "SIGNUP"));
        personalGroups.createFor(user);
        return new SignupResponse(user.getId(), user.getUsername(), user.getEmail(), user.getName());
    }

    @Transactional
    public void sendVerification(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        if (users.existsByEmailIgnoreCase(email)) throw conflict("EMAIL_EXISTS", "이미 가입된 이메일입니다.");
        String code = oneTimeTokens.issueCode(email);
        mail.sendBestEffort(email, "[퇴사] 이메일 인증번호", "인증번호: " + code + "\n10분 안에 입력해 주세요.");
    }

    @Transactional(readOnly = true)
    public void verifyCode(String email, String code) {
        oneTimeTokens.verifyCode(normalizeEmail(email), code);
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private ApplicationException conflict(String code, String message) {
        return new ApplicationException(code, HttpStatus.CONFLICT, message);
    }
}
