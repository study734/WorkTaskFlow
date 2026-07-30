package com.teamproject.authentication;

import com.teamproject.TeamProjectApplication;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.authentication.domain.token.OneTimeToken;
import com.teamproject.authentication.domain.token.OneTimeTokenRepository;
import com.teamproject.authentication.infrastructure.crypto.HashService;
import com.teamproject.authentication.infrastructure.web.RefreshCookieService;
import com.teamproject.jwt.JwtService;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserConsentRepository;
import com.teamproject.user.domain.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
@Transactional
class AuthSecurityApiTest {
    private static final String SECRET = "test-secret-that-is-long-enough-for-hmac-sha-256";
    @Autowired MockMvc mvc;
    @Autowired SignupService signupService;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired UserConsentRepository consents;
    @Autowired OneTimeTokenRepository tokenRepository;
    @Autowired HashService hashService;
    @Autowired ObjectMapper objectMapper;

    @Test
    void loginReturnsAccessTokenAndProtectedRefreshCookieOnly() throws Exception {
        signup("cookie_user", "cookie@example.com");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cookie_user\",\"password\":\"password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(result -> {
                    String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
                    assertThat(cookie).contains("team_refresh_token=", "HttpOnly", "SameSite=Lax", "Path=/api/v1/auth")
                            .doesNotContain("Secure");
                });
    }

    @Test
    void tamperedAndExpiredAccessTokensAreRejected() throws Exception {
        User user = signup("jwt_user", "jwt@example.com");
        String valid = new JwtService(SECRET, 3600).create(user);
        char replacement = valid.charAt(valid.length() - 1) == 'a' ? 'b' : 'a';
        String tampered = valid.substring(0, valid.length() - 1) + replacement;

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        JwtService expiredJwt = new JwtService(SECRET, -1);
        String expired = expiredJwt.create(user);
        assertThatThrownBy(() -> expiredJwt.parse(expired)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void oauthHandshakePrincipalCannotReplaceApiJwtAuthentication() throws Exception {
        signup("oauth_session_user", "oauth-session@example.com");
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"oauth_session_user\",\"password\":\"password123!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(response).get("accessToken").asText();

        mvc.perform(get("/api/v1/auth/me").with(oauth2Login()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mvc.perform(get("/api/v1/auth/me")
                        .with(oauth2Login())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("oauth_session_user"));
    }

    @Test
    void splitControllersKeepSignupRecoveryAndProviderContracts() throws Exception {
        String email = "contract@example.com";
        String code = oneTimeTokens.issueCode(email);

        mvc.perform(post("/api/v1/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"contract@example.com\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"contract_user\",\"email\":\"contract@example.com\",\"name\":\"계약 사용자\",\"password\":\"password123!\",\"verificationCode\":\"" + code + "\",\"termsAgreed\":true,\"privacyAgreed\":true,\"ageConfirmed\":true,\"notificationAgreed\":false,\"marketingAgreed\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("contract_user"));
        User contractUser = users.findByUsernameIgnoreCase("contract_user").orElseThrow();
        assertThat(consents.findAllByUserId(contractUser.getId())).hasSize(5);
        mvc.perform(post("/api/v1/auth/username-reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"contract@example.com\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.google").value(false))
                .andExpect(jsonPath("$.kakao").value(false));
    }

    @Test
    void publicDemoIssuesAReadOnlySession() throws Exception {
        signup("demo_leader", "demo-leader@local.test");
        String response = mvc.perform(post("/api/v1/auth/demo-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode token = objectMapper.readTree(response);

        mvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("demo_leader"));
        mvc.perform(post("/api/v1/groups")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEMO_READ_ONLY"));
    }

    @Test
    void recoveryRequestsDoNotRevealWhetherAccountExists() throws Exception {
        signup("recovery_user", "recovery@example.com");

        for (String path : new String[]{"/api/v1/auth/username-reminders", "/api/v1/auth/password-resets"}) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"recovery@example.com\"}"))
                    .andExpect(status().isNoContent())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"missing@example.com\"}"))
                    .andExpect(status().isNoContent())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
        }
    }

    @Test
    void passwordResetTokenIsSingleUseAndExpiredTokenIsRejected() throws Exception {
        signup("reset_user", "reset@example.com");
        String resetToken = oneTimeTokens.issueResetToken("reset@example.com");
        String request = "{\"email\":\"reset@example.com\",\"token\":\"" + resetToken
                + "\",\"newPassword\":\"newPassword123!\"}";

        mvc.perform(post("/api/v1/auth/password-resets/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/password-resets/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        signup("expired_user", "expired@example.com");
        String expiredRaw = "expired-reset-token";
        tokenRepository.save(new OneTimeToken("expired@example.com", OneTimeToken.Purpose.PASSWORD_RESET,
                hashService.sha256(expiredRaw), LocalDateTime.now().minusSeconds(1)));
        mvc.perform(post("/api/v1/auth/password-resets/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"expired@example.com\",\"token\":\"expired-reset-token\",\"newPassword\":\"newPassword123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void refreshCookieRotatesAndOldCookieCannotBeReused() throws Exception {
        signup("rotation_user", "rotation@example.com");
        var login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"rotation_user\",\"password\":\"password123!\"}"))
                .andExpect(status().isOk()).andReturn();
        Cookie original = cookie(login.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        var refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(original))
                .andExpect(status().isOk()).andReturn();
        Cookie rotated = cookie(refreshed.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertThat(rotated.getValue()).isNotEqualTo(original.getValue());

        mvc.perform(post("/api/v1/auth/refresh").cookie(original))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));

        mvc.perform(post("/api/v1/auth/refresh").cookie(rotated))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
    }

    @Test
    void pwaLoginUsesThirtyDaySlidingCookie() throws Exception {
        signup("pwa_user", "pwa@example.com");

        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Client-Mode", "PWA")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"pwa_user\",\"password\":\"password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                        .contains("Max-Age=2592000"));
    }

    @Test
    void logoutAllRevokesRefreshTokensAndExistingAccessTokensImmediately() throws Exception {
        signup("all_logout_user", "all-logout@example.com");
        var first = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"all_logout_user\",\"password\":\"password123!\"}"))
                .andExpect(status().isOk()).andReturn();
        var second = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"all_logout_user\",\"password\":\"password123!\"}"))
                .andExpect(status().isOk()).andReturn();
        String firstAccess = objectMapper.readTree(first.getResponse().getContentAsString()).get("accessToken").asText();
        String secondAccess = objectMapper.readTree(second.getResponse().getContentAsString()).get("accessToken").asText();
        Cookie secondRefresh = cookie(second.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        mvc.perform(post("/api/v1/auth/logout-all").header("Authorization", "Bearer " + firstAccess))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + secondAccess))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(secondRefresh))
                .andExpect(status().isUnauthorized());
    }

    private User signup(String username, String email) {
        String code = oneTimeTokens.issueCode(email);
        signupService.signup(new SignupRequest(username, email, "보안 사용자", "password123!", code));
        User user = users.findByUsernameIgnoreCase(username).orElseThrow();
        assertThat(user.getId()).isNotNull();
        return user;
    }

    private Cookie cookie(String header) {
        assertThat(header).isNotBlank();
        String pair = header.substring(0, header.indexOf(';'));
        return new Cookie(RefreshCookieService.NAME, pair.substring(pair.indexOf('=') + 1));
    }
}
