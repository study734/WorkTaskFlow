package com.teamproject.authorization.config;

import com.teamproject.authentication.infrastructure.oauth.OAuth2SuccessHandler;
import com.teamproject.jwt.JwtAuthenticationFilter;
import com.teamproject.admin.config.AdminAccessFilter;
import com.teamproject.admin.config.AdminMfaAuthorizationFilter;
import com.teamproject.admin.config.AdminAuditFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.cors.*;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
            SecurityAuditFilter auditFilter, SensitiveEndpointRateLimitFilter rateLimitFilter,
            DemoReadOnlyFilter demoReadOnlyFilter, OAuth2SuccessHandler successHandler,
            SameOriginMutationFilter sameOriginFilter, AdminAccessFilter adminAccessFilter,
            AdminMfaAuthorizationFilter adminMfaFilter, AdminAuditFilter adminAuditFilter,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.admin.frontend-url:}") String adminFrontendUrl) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfiguration(frontendUrl, adminFrontendUrl)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor((request, response, exception) -> {
                    response.setStatus(org.springframework.http.HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
                    response.getWriter().write("{\"code\":\"AUTHENTICATION_REQUIRED\",\"message\":\"로그인이 필요합니다.\",\"fieldErrors\":null}");
                }, new AntPathRequestMatcher("/api/**")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/health/**", "/api/v1/auth/providers",
                                "/api/v1/auth/oauth-signup").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/email-verifications/**",
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                "/api/v1/auth/demo-session",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/oauth-signup/complete",
                                "/api/v1/auth/username-reminders",
                                "/api/v1/auth/password-resets/**").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/auth/oauth-signup").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth.successHandler(successHandler)
                        .failureHandler((request, response, exception) -> response.sendRedirect(frontendUrl + "/login?socialError=SOCIAL_LOGIN_FAILED")))
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(sameOriginFilter, SensitiveEndpointRateLimitFilter.class)
                .addFilterBefore(adminAccessFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(adminMfaFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(adminAuditFilter, AdminMfaAuthorizationFilter.class)
                .addFilterAfter(demoReadOnlyFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(auditFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean FilterRegistrationBean<SecurityAuditFilter> auditFilterRegistration(SecurityAuditFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    @Bean FilterRegistrationBean<SensitiveEndpointRateLimitFilter> rateLimitFilterRegistration(
            SensitiveEndpointRateLimitFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    @Bean FilterRegistrationBean<DemoReadOnlyFilter> demoReadOnlyFilterRegistration(DemoReadOnlyFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    @Bean FilterRegistrationBean<SameOriginMutationFilter> sameOriginFilterRegistration(SameOriginMutationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    @Bean FilterRegistrationBean<AdminAccessFilter> adminAccessFilterRegistration(AdminAccessFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    @Bean FilterRegistrationBean<AdminMfaAuthorizationFilter> adminMfaFilterRegistration(AdminMfaAuthorizationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    @Bean FilterRegistrationBean<AdminAuditFilter> adminAuditFilterRegistration(AdminAuditFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    private CorsConfigurationSource corsConfiguration(String frontendUrl, String adminFrontendUrl) {
        var config = new CorsConfiguration();
        var origins = new java.util.ArrayList<String>();
        origins.add(frontendUrl);
        if (adminFrontendUrl != null && !adminFrontendUrl.isBlank()) origins.add(adminFrontendUrl);
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Client-Mode",
                "X-Device-Id", "X-Device-Name"));
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
