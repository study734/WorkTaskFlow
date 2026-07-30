package com.teamproject.authentication.infrastructure.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookieService {
    // Keep the existing cookie name so deployed sessions survive the product rename.
    public static final String NAME = "team_refresh_token";
    private final boolean secure;
    public RefreshCookieService(@Value("${app.jwt.secure-cookie}") boolean secure) {
        this.secure = secure;
    }
    public void add(HttpServletResponse response, String value, long maxAgeSeconds) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(value, maxAgeSeconds).toString());
    }
    public void clear(HttpServletResponse response) { response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0).toString()); }
    private ResponseCookie cookie(String value, long age) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite("Lax").path("/api/v1/auth").maxAge(age).build();
    }
}
