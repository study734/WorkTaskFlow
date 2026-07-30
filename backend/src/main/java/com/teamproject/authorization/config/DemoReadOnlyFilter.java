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
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
    }
}
