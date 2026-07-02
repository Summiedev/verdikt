package com.verdikt.verdikt_backend.config;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitConfig rateLimitConfig;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        String ip = extractClientIp(request);

        Bucket bucket = null;

        if ("POST".equals(method) && path.equals("/api/rooms")) {
            bucket = rateLimitConfig.resolveRoomCreationBucket(ip);
        } else if ("POST".equals(method) && path.matches("/api/rooms/.+/votes")) {
    String playerToken = request.getHeader("X-Player-Token");
    String voteKey = (playerToken != null && !playerToken.isEmpty()) ? playerToken : ip;
    bucket = rateLimitConfig.resolveVoteBucket(voteKey);
}

        if (bucket != null) {
            if (bucket.tryConsume(1)) {
                filterChain.doFilter(request, response);
            } else {
                response.setStatus(429); // Too Many Requests
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"errorCode\":\"RATE_LIMITED\",\"message\":\"Too many requests. Please slow down.\"}"
                );
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
}