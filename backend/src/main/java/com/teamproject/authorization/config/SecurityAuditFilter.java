package com.teamproject.authorization.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class SecurityAuditFilter extends OncePerRequestFilter {
    private static final Logger audit = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{8,80}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestId(request);
        long started = System.nanoTime();
        response.setHeader("X-Request-Id", requestId);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Cross-Origin-Resource-Policy", "same-site");
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
        }
        try {
            chain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            boolean mutating = !"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())
                    && !"OPTIONS".equals(request.getMethod());
            if (mutating || status == 401 || status == 403 || status == 429) {
                Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null
                        : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                long durationMs = (System.nanoTime() - started) / 1_000_000;
                audit.info("event=HTTP_SECURITY_AUDIT requestId={} method={} path={} status={} actorUserId={} remoteAddress={} durationMs={} userAgent={}",
                        requestId, request.getMethod(), request.getRequestURI(), status,
                        principal instanceof Long ? principal : "anonymous", safe(request.getRemoteAddr(), 64),
                        durationMs, safe(request.getHeader("User-Agent"), 180));
            }
        }
    }

    private String requestId(HttpServletRequest request) {
        String supplied = request.getHeader("X-Request-Id");
        return supplied != null && SAFE_REQUEST_ID.matcher(supplied).matches()
                ? supplied : UUID.randomUUID().toString();
    }

    private String safe(String value, int maxLength) {
        if (value == null) return "-";
        String cleaned = value.replaceAll("[\\r\\n\\t]", "_");
        return cleaned.substring(0, Math.min(cleaned.length(), maxLength));
    }
}
