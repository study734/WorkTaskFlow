package com.teamproject.authorization.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SameOriginMutationFilter extends OncePerRequestFilter {
    private final String frontendOrigin;
    private final String adminFrontendOrigin;

    public SameOriginMutationFilter(@Value("${app.frontend-url}") String frontendOrigin,
            @Value("${app.admin.frontend-url:}") String adminFrontendOrigin) {
        this.frontendOrigin = stripTrailingSlash(frontendOrigin);
        this.adminFrontendOrigin = stripTrailingSlash(adminFrontendOrigin);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)
                || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        String origin = request.getHeader("Origin");
        if ("cross-site".equalsIgnoreCase(fetchSite) || (origin != null && !allowed(origin, request))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":\"CROSS_ORIGIN_REQUEST_BLOCKED\",\"message\":\"허용되지 않은 요청 출처입니다.\",\"fieldErrors\":null}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean allowed(String origin, HttpServletRequest request) {
        String requestOrigin = request.getScheme() + "://" + request.getServerName()
                + (defaultPort(request) ? "" : ":" + request.getServerPort());
        String normalized = stripTrailingSlash(origin);
        return normalized.equals(frontendOrigin)
                || (!adminFrontendOrigin.isBlank() && normalized.equals(adminFrontendOrigin))
                || normalized.equals(requestOrigin);
    }

    private boolean defaultPort(HttpServletRequest request) {
        return ("http".equals(request.getScheme()) && request.getServerPort() == 80)
                || ("https".equals(request.getScheme()) && request.getServerPort() == 443);
    }

    private String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
