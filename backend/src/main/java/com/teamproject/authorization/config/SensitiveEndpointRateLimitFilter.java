package com.teamproject.authorization.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class SensitiveEndpointRateLimitFilter extends OncePerRequestFilter {
    private static final Logger audit = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final Pattern AI_REPORT_GENERATION =
            Pattern.compile("/api/v1/groups/\\d+/reports/ai-weekly");
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final int attempts;
    private final long windowSeconds;

    public SensitiveEndpointRateLimitFilter(
            @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.attempts:10}") int attempts,
            @Value("${app.security.rate-limit.window-seconds:60}") long windowSeconds) {
        this.enabled = enabled;
        this.attempts = attempts;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled || !"POST".equals(request.getMethod())) return true;
        String path = request.getRequestURI();
        return !path.equals("/api/v1/auth/login")
                && !path.equals("/api/v1/auth/demo-session")
                && !path.equals("/api/v1/auth/refresh")
                && !path.equals("/api/v1/auth/logout-all")
                && !path.equals("/api/v1/auth/oauth-signup/complete")
                && !path.equals("/api/v1/groups/join")
                && !path.startsWith("/api/v1/payments")
                && !path.startsWith("/api/v1/auth/password-resets")
                && !path.startsWith("/api/v1/auth/email-verifications")
                // AI 리포트 생성은 한 번이 유료 provider 호출이다. 서버 상한은 (그룹, 기간,
                // 언어)당 3회뿐이라 과거 기간을 순회하면 호출 수에 제한이 없다.
                && !AI_REPORT_GENERATION.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = Instant.now().getEpochSecond();
        String key = request.getRemoteAddr() + ":" + request.getRequestURI();
        Window current = windows.compute(key, (ignored, old) -> old == null || now >= old.resetAt
                ? new Window(1, now + windowSeconds) : new Window(old.count + 1, old.resetAt));
        if (windows.size() > 10_000) windows.entrySet().removeIf(entry -> now >= entry.getValue().resetAt);
        response.setHeader("X-RateLimit-Limit", String.valueOf(attempts));
        response.setHeader("X-RateLimit-Reset", String.valueOf(current.resetAt));
        if (current.count > attempts) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", String.valueOf(Math.max(1, current.resetAt - now)));
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\",\"fieldErrors\":null}");
            audit.warn("event=RATE_LIMIT_EXCEEDED outcome=BLOCKED path={} remoteAddress={}",
                    request.getRequestURI(), request.getRemoteAddr());
            return;
        }
        chain.doFilter(request, response);
    }

    private record Window(int count, long resetAt) {}
}
