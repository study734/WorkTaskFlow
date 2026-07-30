package com.teamproject.admin.config;

import com.teamproject.admin.application.AdminAuditService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class AdminAuditFilter extends OncePerRequestFilter {
    private final AdminAuditService audit;
    public AdminAuditFilter(AdminAuditService audit) { this.audit = audit; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/admin");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            Long actorId = authentication != null && authentication.getPrincipal() instanceof Long id ? id : null;
            try {
                audit.record(actorId, request.getMethod(), request.getRequestURI(), response.getStatus(),
                        request.getRemoteAddr(), request.getHeader("User-Agent"), request.getHeader("X-Request-Id"));
            } catch (RuntimeException ignored) {
                org.slf4j.LoggerFactory.getLogger(AdminAuditFilter.class)
                        .error("Failed to persist admin audit log. method={} path={} status={}",
                                request.getMethod(), request.getRequestURI(), response.getStatus());
            }
        }
    }
}
