package com.teamproject.admin.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

@Component
public class AdminAccessFilter extends OncePerRequestFilter {
    private final boolean enabled;
    private final int port;
    private final List<String> allowed;
    private final List<String> trustedProxies;
    public AdminAccessFilter(@Value("${app.admin.enabled:false}") boolean enabled,
            @Value("${app.admin.port:19092}") int port,
            @Value("${app.admin.allowed-ips:127.0.0.1,::1}") String allowedIps,
            @Value("${app.admin.trusted-proxies:127.0.0.1,::1}") String trustedProxyIps) {
        this.enabled = enabled; this.port = port;
        this.allowed = rules(allowedIps);
        this.trustedProxies = rules(trustedProxyIps);
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/admin");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String clientAddress = clientAddress(request);
        if (!enabled || request.getLocalPort() != port
                || allowed.stream().noneMatch(rule -> matches(clientAddress, rule))) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"ADMIN_ENDPOINT_NOT_FOUND\",\"message\":\"요청한 경로를 찾을 수 없습니다.\",\"fieldErrors\":null}");
            return;
        }
        chain.doFilter(request, response);
    }
    private String clientAddress(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (trustedProxies.stream().noneMatch(rule -> matches(peer, rule))) return peer;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return peer;
        String candidate = forwarded.split(",", 2)[0].trim();
        try {
            InetAddress.getByName(candidate);
            return candidate;
        } catch (java.net.UnknownHostException exception) {
            return peer;
        }
    }
    private List<String> rules(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(rule -> !rule.isBlank()).toList();
    }
    private boolean matches(String address, String rule) {
        if (!rule.contains("/")) return address.equals(rule);
        try {
            String[] values = rule.split("/");
            byte[] candidate = InetAddress.getByName(address).getAddress();
            byte[] network = InetAddress.getByName(values[0]).getAddress();
            int prefix = Integer.parseInt(values[1]);
            if (candidate.length != network.length || prefix < 0 || prefix > candidate.length * 8) return false;
            for (int index = 0; index < candidate.length; index++) {
                int bits = Math.min(8, Math.max(0, prefix - index * 8));
                int mask = bits == 0 ? 0 : 0xff << (8 - bits);
                if ((candidate[index] & mask) != (network[index] & mask)) return false;
            }
            return true;
        } catch (RuntimeException | java.net.UnknownHostException exception) { return false; }
    }
}
