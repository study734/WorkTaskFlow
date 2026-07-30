package com.teamproject.jwt;

import com.teamproject.user.domain.UserRepository;
import com.teamproject.authentication.domain.token.RefreshTokenRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    public JwtAuthenticationFilter(JwtService jwtService, UserRepository users, RefreshTokenRepository refreshTokens) {
        this.jwtService = jwtService;
        this.users = users;
        this.refreshTokens = refreshTokens;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var claims = jwtService.parse(header.substring(7));
                Long userId = Long.valueOf(claims.getSubject());
                Long authVersion = claims.get("authVersion", Long.class);
                String sessionId = claims.get("sessionId", String.class);
                var now = java.time.LocalDateTime.now();
                if (users.findById(userId)
                        .filter(user -> user.isActive() && authVersion != null && user.getAuthVersion() == authVersion)
                        .isEmpty()
                        || sessionId == null
                        || !refreshTokens.existsBySessionIdAndRevokedAtIsNullAndExpiresAtAfterAndAbsoluteExpiresAtAfter(
                                sessionId, now, now)) {
                    SecurityContextHolder.clearContext();
                    chain.doFilter(request, response);
                    return;
                }
                var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + claims.get("role", String.class)));
                if (Boolean.TRUE.equals(claims.get("mfaVerified", Boolean.class))) {
                    authorities.add(new SimpleGrantedAuthority("MFA_VERIFIED"));
                }
                var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        } else if (request.getRequestURI().startsWith("/api/")) {
            // OAuth2 uses a short-lived HTTP session only for its redirect handshake.
            // Never let that OIDC principal authenticate application API requests.
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }
}
