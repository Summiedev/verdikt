package com.verdikt.verdikt_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
@RequiredArgsConstructor
public class GlobalRateLimitFilter extends OncePerRequestFilter {

    @Value("${app.rate-limit.global-capacity:200}")
    private int globalCapacity;

    @Value("${app.rate-limit.global-refill-period:1}")
    private int globalRefillPeriodMinutes;

    private final RedisRateLimiter redisRateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || request.getRequestURI().equals("/actuator/health/liveness")) {
            filterChain.doFilter(request, response);
            return;
        }
        String ip = extractClientIp(request);
        if (redisRateLimiter.tryConsume("global", ip, globalCapacity,
                Duration.ofMinutes(globalRefillPeriodMinutes))) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Global rate limit exceeded for IP: {}", ip);
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Retry-After", "60");
            String requestId = response.getHeader("X-Request-ID");
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Please slow down.\",\"requestId\":"
                    + (requestId == null ? "null" : "\"" + requestId + "\"") + "}");
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
