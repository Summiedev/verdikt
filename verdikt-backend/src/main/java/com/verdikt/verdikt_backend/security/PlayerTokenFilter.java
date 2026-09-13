package com.verdikt.verdikt_backend.security;

import com.verdikt.verdikt_backend.exception.InvalidTokenException;
import com.verdikt.verdikt_backend.exception.PlayerNotFoundException;
import com.verdikt.verdikt_backend.exception.TokenExpiredException;
import com.verdikt.verdikt_backend.model.Player;
import com.verdikt.verdikt_backend.repository.PlayerRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
@Slf4j
public class PlayerTokenFilter extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "X-Player-Token";
    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/api/rooms",
            "/api/rooms/join",
            "/actuator/health/liveness",
            "/actuator/info",
            "/error"
    );

    private final PlayerRepository playerRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isPublicEndpoint(path, request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String playerTokenHeader = request.getHeader(TOKEN_HEADER);
        String token = null;

        if (playerTokenHeader != null && !playerTokenHeader.isBlank()) {
            token = playerTokenHeader.trim();
        } else if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        }

        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            java.util.UUID tokenUuid = java.util.UUID.fromString(token);
            LocalDateTime now = LocalDateTime.now();

            Player player = playerRepository.findByTokenAndExpiresAtAfter(tokenUuid, now)
                    .orElseThrow(() -> new TokenExpiredException("Session not found or expired. Please join again."));

            PlayerAuthentication authentication = new PlayerAuthentication(player);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (IllegalArgumentException ex) {
            log.debug("Invalid player token format");
        } catch (PlayerNotFoundException | TokenExpiredException ex) {
            log.debug("Player auth failed: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(String path, String method) {
        return PUBLIC_ENDPOINTS.contains(path)
                || ("OPTIONS".equalsIgnoreCase(method))
                || (path.startsWith("/ws") && !path.contains("/app/"));
    }
}
