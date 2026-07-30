package com.teamproject.admin.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;

@Component
public class AdminMfaAuthorizationFilter extends OncePerRequestFilter {
    private static final Set<String> ENROLLMENT_PATHS = Set.of(
            "/api/v1/admin/mfa/status", "/api/v1/admin/mfa/setup", "/api/v1/admin/mfa/confirm");
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/admin") || ENROLLMENT_PATHS.contains(request.getRequestURI());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean verified = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(value -> "MFA_VERIFIED".equals(value.getAuthority()));
        if (!verified) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"ADMIN_MFA_REQUIRED\",\"message\":\"관리자 MFA 인증이 필요합니다.\",\"fieldErrors\":null}");
            return;
        }
        chain.doFilter(request, response);
    }
}
