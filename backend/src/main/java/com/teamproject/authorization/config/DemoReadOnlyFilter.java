package com.teamproject.authorization.config;

import com.teamproject.user.domain.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class DemoReadOnlyFilter extends OncePerRequestFilter {
    private final UserRepository users;

    public DemoReadOnlyFilter(UserRepository users) { this.users = users; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS")
                || path.equals("/api/v1/auth/demo-session")
                || path.equals("/api/v1/auth/logout")
                || path.equals("/api/v1/auth/refresh");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        boolean demo = principal instanceof Long userId
                && users.findById(userId).map(user -> user.getUsername().startsWith("demo_")).orElse(false);
        if (!demo) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("""
                {"code":"DEMO_READ_ONLY","message":"공용 데모에서는 데이터를 변경할 수 없습니다.","fieldErrors":null}
                """.trim());
    }
}
