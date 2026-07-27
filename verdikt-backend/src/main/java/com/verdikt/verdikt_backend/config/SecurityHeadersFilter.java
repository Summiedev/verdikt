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
import java.util.List;

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

        if (isAllowedOrigin(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Player-Token, Authorization");
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

        String csp;
        String requestUri = request.getRequestURI();

        if (requestUri.startsWith("/ws") || requestUri.startsWith("/topic") || requestUri.startsWith("/app")) {
            csp = "default-src 'self'; connect-src 'self' " + String.join(" ", getCspOrigins()) + " ws://localhost:8080 wss://localhost:8080; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";
        } else {
            csp = "default-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";
        }
        response.setHeader("Content-Security-Policy", csp);

        String scheme = request.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAllowedOrigin(String origin) {
        if (origin == null) return false;
        for (String allowed : allowedOrigins) {
            if (allowed.equals("*")) return true;
            if (allowed.equals(origin)) return true;
        }
        return false;
    }

    private String[] getCspOrigins() {
        List<String> httpsOrigins = new java.util.ArrayList<>();
        for (String origin : allowedOrigins) {
            if (!"*".equals(origin) && origin.startsWith("https://")) {
                httpsOrigins.add(origin);
            }
        }
        return httpsOrigins.toArray(new String[0]);
    }
}
