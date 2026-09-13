package com.verdikt.verdikt_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Value("${app.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String origin = request.getHeader("Origin");
        String requestScheme = request.getScheme();
        String requestServer = request.getServerName();
        int requestPort = request.getServerPort();
        String currentHost = requestPort == -1 ? requestServer : requestServer + ":" + requestPort;

        if (isAllowedOrigin(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Player-Token, Authorization, X-Request-ID, X-Trace-ID");
            response.setHeader("Access-Control-Max-Age", "3600");
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");

        String csp = buildContentSecurityPolicy(request, currentHost);
        response.setHeader("Content-Security-Policy", csp);

        if ("https".equalsIgnoreCase(requestScheme)) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        }

        filterChain.doFilter(request, response);
    }

    private String buildContentSecurityPolicy(HttpServletRequest request, String currentHost) {
        StringBuilder csp = new StringBuilder();
        csp.append("default-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");

        Set<String> origins = new HashSet<>();
        for (String allowed : allowedOrigins) {
            if (!"*".equals(allowed)) {
                origins.add(allowed.trim());
            }
        }

        String wsScheme = "https".equalsIgnoreCase(request.getScheme()) ? "wss" : "ws";
        origins.add(wsScheme + "://" + currentHost);

        StringBuilder connectSrc = new StringBuilder(" connect-src 'self'");
        for (String o : origins) {
            connectSrc.append(" ").append(o);
        }
        csp.append(connectSrc);

        return csp.toString();
    }

    private boolean isAllowedOrigin(String origin) {
        if (origin == null) return false;
        for (String allowed : allowedOrigins) {
            if (allowed.trim().equals("*")) return true;
            if (allowed.trim().equals(origin)) return true;
        }
        return false;
    }
}
