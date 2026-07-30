package com.teamproject.authentication.presentation;

import com.teamproject.authentication.application.SessionService;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SessionDtos.MeResponse;
import com.teamproject.authentication.application.dto.SessionDtos.TokenResponse;
import com.teamproject.authentication.infrastructure.web.RefreshCookieService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.teamproject.authentication.domain.token.RefreshToken.ClientMode;
import com.teamproject.authentication.application.dto.SessionDtos.DeviceSessionsResponse;
import com.teamproject.authentication.infrastructure.web.SessionDeviceResolver;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
public class SessionController {
    private final SessionService sessions;
    private final RefreshCookieService cookies;
    private final SessionDeviceResolver devices;
    public SessionController(SessionService sessions, RefreshCookieService cookies, SessionDeviceResolver devices) {
        this.sessions = sessions;
        this.cookies = cookies;
        this.devices = devices;
    }
    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request,
            @RequestHeader(name = "X-Client-Mode", required = false) String clientMode,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        var tokens = sessions.login(request,
                "PWA".equalsIgnoreCase(clientMode) ? ClientMode.PWA : ClientMode.WEB,
                devices.resolve(servletRequest));
        cookies.add(response, tokens.refreshToken(), tokens.refreshCookieMaxAgeSeconds());
        return tokens.response();
    }
    @PostMapping("/demo-session")
    TokenResponse demo(HttpServletResponse response) {
        var tokens = sessions.demo();
        cookies.add(response, tokens.refreshToken(), tokens.refreshCookieMaxAgeSeconds());
        return tokens.response();
    }
    @PostMapping("/logout-all")
    ResponseEntity<Void> logoutAll(Authentication authentication, HttpServletResponse response) {
        sessions.logoutAll((Long) authentication.getPrincipal());
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/refresh")
    TokenResponse refresh(@CookieValue(name = RefreshCookieService.NAME, required = false) String refreshToken,
            @RequestHeader(name = "X-Client-Mode", required = false) String clientMode,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        var tokens = sessions.refresh(refreshToken,
                "PWA".equalsIgnoreCase(clientMode) ? ClientMode.PWA : null,
                devices.resolve(servletRequest));
        cookies.add(response, tokens.refreshToken(), tokens.refreshCookieMaxAgeSeconds());
        return tokens.response();
    }
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = RefreshCookieService.NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        sessions.logout(refreshToken);
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/me")
    MeResponse me(Authentication authentication) { return sessions.me((Long) authentication.getPrincipal()); }

    @GetMapping("/sessions")
    DeviceSessionsResponse sessions(Authentication authentication,
            @CookieValue(name = RefreshCookieService.NAME, required = false) String refreshToken) {
        return sessions.sessions((Long) authentication.getPrincipal(), refreshToken);
    }

    @DeleteMapping("/sessions/{sessionId}")
    ResponseEntity<Void> logoutSession(Authentication authentication, @PathVariable String sessionId,
            @CookieValue(name = RefreshCookieService.NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (sessions.logoutSession((Long) authentication.getPrincipal(), sessionId, refreshToken)) {
            cookies.clear(response);
        }
        return ResponseEntity.noContent().build();
    }
}
