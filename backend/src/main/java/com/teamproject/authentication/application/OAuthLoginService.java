package com.teamproject.authentication.application;

import com.teamproject.authentication.domain.oauth.SocialAccount;
import com.teamproject.authentication.domain.oauth.SocialAccountRepository;
import com.teamproject.authentication.domain.oauth.OAuthSignupRequest;
import com.teamproject.authentication.domain.oauth.OAuthSignupRequestRepository;
import com.teamproject.authentication.infrastructure.crypto.HashService;
import com.teamproject.authentication.application.dto.OAuthDtos.SignupCompleteRequest;
import com.teamproject.authentication.application.dto.OAuthDtos.SignupStatusResponse;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.PersonalGroupProvisioner;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserConsent;
import com.teamproject.user.domain.UserConsentRepository;
import com.teamproject.user.domain.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import com.teamproject.authentication.domain.token.RefreshToken.ClientMode;
import com.teamproject.authentication.domain.token.SessionDevice;

@Service
public class OAuthLoginService {
    public static final String CONSENT_POLICY_VERSION = "2026-07-25-v2";
    private static final long SIGNUP_MINUTES = 10;
    private final SecureRandom random = new SecureRandom();
    private final UserRepository users;
    private final SocialAccountRepository socialAccounts;
    private final OAuthSignupRequestRepository signupRequests;
    private final UserConsentRepository consents;
    private final HashService hashes;
    private final AccessSessionIssuer issuer;
    private final PersonalGroupProvisioner personalGroups;

    public OAuthLoginService(UserRepository users, SocialAccountRepository socialAccounts,
            OAuthSignupRequestRepository signupRequests, UserConsentRepository consents,
            HashService hashes, AccessSessionIssuer issuer, PersonalGroupProvisioner personalGroups) {
        this.users = users;
        this.socialAccounts = socialAccounts;
        this.signupRequests = signupRequests;
        this.consents = consents;
        this.hashes = hashes;
        this.issuer = issuer;
        this.personalGroups = personalGroups;
    }

    @Transactional
    public StartResult start(String provider, String subject, String rawEmail, String name, boolean emailVerified) {
        return start(provider, subject, rawEmail, name, emailVerified, ClientMode.WEB, SessionDevice.unknown());
    }

    @Transactional
    public StartResult start(String provider, String subject, String rawEmail, String name, boolean emailVerified,
            ClientMode mode, SessionDevice device) {
        IssuedTokens existing = socialAccounts.findByProviderAndProviderSubject(provider, subject)
                .map(SocialAccount::getUser)
                .map(user -> {
                    user.recordLogin();
                    return issuer.issue(user, mode, device);
                })
                .orElse(null);
        if (existing != null) return new StartResult(existing, null);
        if ("google".equals(provider) && !emailVerified) {
            throw new ApplicationException("SOCIAL_EMAIL_UNVERIFIED", HttpStatus.BAD_REQUEST,
                    "Google에서 확인된 이메일 계정만 가입할 수 있습니다.");
        }
        String email = normalizeAndValidateEmail(rawEmail);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApplicationException("SOCIAL_ACCOUNT_LINK_REQUIRED", HttpStatus.CONFLICT,
                    "같은 이메일의 기존 계정이 있습니다. 로그인 후 계정 연결이 필요합니다.");
        }
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random.generateSeed(48));
        signupRequests.save(new OAuthSignupRequest(hashes.sha256(rawToken), provider, subject,
                email, cleanName(name), LocalDateTime.now().plusMinutes(SIGNUP_MINUTES)));
        return new StartResult(null, rawToken);
    }

    @Transactional(readOnly = true)
    public SignupStatusResponse status(String rawToken) {
        OAuthSignupRequest pending = findUsable(rawToken);
        return new SignupStatusResponse(pending.getProvider(), pending.getEmail(),
                pending.getName(), pending.getExpiresAt());
    }

    @Transactional
    public IssuedTokens complete(String rawToken, SignupCompleteRequest request) {
        return complete(rawToken, request, ClientMode.WEB, SessionDevice.unknown());
    }

    @Transactional
    public IssuedTokens complete(String rawToken, SignupCompleteRequest request, ClientMode mode, SessionDevice device) {
        OAuthSignupRequest pending = findUsableLocked(rawToken);
        if (socialAccounts.findByProviderAndProviderSubject(
                pending.getProvider(), pending.getProviderSubject()).isPresent()) {
            signupRequests.delete(pending);
            throw new ApplicationException("SOCIAL_ACCOUNT_ALREADY_REGISTERED", HttpStatus.CONFLICT,
                    "이미 가입된 Google 계정입니다. 다시 로그인해 주세요.");
        }
        if (users.existsByEmailIgnoreCase(pending.getEmail())) {
            signupRequests.delete(pending);
            throw new ApplicationException("SOCIAL_ACCOUNT_LINK_REQUIRED", HttpStatus.CONFLICT,
                    "같은 이메일의 기존 계정이 있습니다. 로그인 후 계정 연결이 필요합니다.");
        }
        User user = users.save(User.social(availableUsername(pending.getEmail()), pending.getEmail(), pending.getName()));
        personalGroups.createFor(user);
        socialAccounts.save(new SocialAccount(user, pending.getProvider(), pending.getProviderSubject()));
        saveConsents(user, request);
        signupRequests.delete(pending);
        user.recordLogin();
        return issuer.issue(user, mode, device);
    }

    @Transactional
    public void cancel(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        signupRequests.findLockedByTokenHash(hashes.sha256(rawToken))
                .filter(value -> value.isUsable(LocalDateTime.now()))
                .ifPresent(signupRequests::delete);
    }

    private OAuthSignupRequest findUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw invalidPending();
        return signupRequests.findByTokenHash(hashes.sha256(rawToken))
                .filter(value -> value.isUsable(LocalDateTime.now()))
                .orElseThrow(this::invalidPending);
    }

    private OAuthSignupRequest findUsableLocked(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw invalidPending();
        return signupRequests.findLockedByTokenHash(hashes.sha256(rawToken))
                .filter(value -> value.isUsable(LocalDateTime.now()))
                .orElseThrow(this::invalidPending);
    }

    private String normalizeAndValidateEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new ApplicationException("SOCIAL_EMAIL_REQUIRED", HttpStatus.BAD_REQUEST,
                    "소셜 계정의 이메일 제공 동의가 필요합니다.");
        }
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    private void saveConsents(User user, SignupCompleteRequest request) {
        String source = "GOOGLE_OAUTH_SIGNUP";
        consents.save(new UserConsent(user, UserConsent.Type.TERMS, CONSENT_POLICY_VERSION, true, source));
        consents.save(new UserConsent(user, UserConsent.Type.PRIVACY_COLLECTION, CONSENT_POLICY_VERSION, true, source));
        consents.save(new UserConsent(user, UserConsent.Type.AGE_14_OR_OLDER, CONSENT_POLICY_VERSION, true, source));
        consents.save(new UserConsent(user, UserConsent.Type.SERVICE_NOTIFICATIONS, CONSENT_POLICY_VERSION,
                request.notificationAgreed(), source));
        consents.save(new UserConsent(user, UserConsent.Type.MARKETING_MESSAGES, CONSENT_POLICY_VERSION,
                request.marketingAgreed(), source));
    }

    private String availableUsername(String email) {
        String base = email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9_]", "");
        if (base.length() < 4) base = "user" + base;
        if (base.length() > 16) base = base.substring(0, 16);
        String candidate = base.toLowerCase(Locale.ROOT);
        int suffix = 1;
        while (users.existsByUsernameIgnoreCase(candidate)) candidate = base + suffix++;
        return candidate;
    }

    private String cleanName(String name) { return name == null || name.isBlank() ? "사용자" : name.trim(); }
    private ApplicationException invalidPending() {
        return new ApplicationException("OAUTH_SIGNUP_EXPIRED", HttpStatus.UNAUTHORIZED,
                "Google 가입 요청이 만료되었습니다. 로그인부터 다시 진행해 주세요.");
    }
    public record StartResult(IssuedTokens tokens, String signupToken) {
        public boolean requiresConsent() { return signupToken != null; }
    }
}
