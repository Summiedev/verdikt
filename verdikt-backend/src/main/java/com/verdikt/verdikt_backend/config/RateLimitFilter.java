package com.verdikt.verdikt_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimiter redisRateLimiter;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();
        String ip = extractClientIp(request);

        String bucket = null;
        String subject = ip;
        int capacity = 0;
        Duration window = Duration.ZERO;

        if ("POST".equals(method) && path.equals("/api/rooms")) {
            bucket = "room-creation";
            capacity = 5;
            window = Duration.ofHours(1);
        } else if ("POST".equals(method) && path.equals("/api/rooms/join")) {
            bucket = "room-join";
            capacity = 30;
            window = Duration.ofMinutes(10);
        } else if (("POST".equals(method) || "DELETE".equals(method))
                && path.matches("/api/rooms/.+/votes")) {
            String playerToken = request.getHeader("X-Player-Token");
            subject = (playerToken != null && !playerToken.isBlank()) ? playerToken.trim() : ip;
            bucket = "vote";
            capacity = 30;
            window = Duration.ofSeconds(10);
        } else if ("POST".equals(method)
                && path.matches("/api/rooms/.+/game/(start|next-question|end)")) {
            String playerToken = request.getHeader("X-Player-Token");
            subject = (playerToken != null && !playerToken.isBlank()) ? playerToken.trim() : ip;
            bucket = "game-transition";
            capacity = 30;
            window = Duration.ofMinutes(1);
        } else if ("GET".equals(method)
                && path.matches("/api/rooms/.+/game/preview-questions")) {
            bucket = "question-preview";
            capacity = 60;
            window = Duration.ofMinutes(1);
        }

        if (bucket != null) {
            if (redisRateLimiter.tryConsume(bucket, subject, capacity, window)) {
                filterChain.doFilter(request, response);
            } else {
                response.setStatus(429); // Too Many Requests
                response.setContentType("application/json");
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("Retry-After", "10");
                response.getWriter().write(rateLimitBody(response));
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim(); // first IP in chain, handles proxies like Railway
        }
        return request.getRemoteAddr();
    }

    private String rateLimitBody(HttpServletResponse response) {
        String requestId = response.getHeader("X-Request-ID");
        return "{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Please slow down.\",\"requestId\":"
                + (requestId == null ? "null" : "\"" + requestId + "\"") + "}";
    }
}
