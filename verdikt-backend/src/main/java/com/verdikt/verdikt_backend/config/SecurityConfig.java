package com.verdikt.verdikt_backend.config;

import com.verdikt.verdikt_backend.security.PlayerTokenFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verdikt.verdikt_backend.dto.response.ApiErrorResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final PlayerTokenFilter playerTokenFilter;
    private final Environment environment;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                    .xssProtection(xxss -> xxss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                    .contentSecurityPolicy(csp -> csp
                            .policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")
                            .reportOnly(false))
                    .frameOptions(frame -> frame.deny())
                    .httpStrictTransportSecurity(hsts -> {
                        if ("https".equalsIgnoreCase(environment.getProperty("server.ssl.enabled", "false"))) {
                            hsts.includeSubDomains(true);
                        }
                    })
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint((request, response, exception) -> writeError(
                            response, org.springframework.http.HttpStatus.UNAUTHORIZED.value(),
                            "INVALID_PLAYER_TOKEN", "A valid player session is required."))
                    .accessDeniedHandler((request, response, exception) -> writeError(
                            response, org.springframework.http.HttpStatus.FORBIDDEN.value(),
                            "FORBIDDEN", "You do not have permission to perform this action."))
            )
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                    .requestMatchers("/actuator/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/rooms").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/rooms/join").permitAll()
                    .requestMatchers("/api/rooms/rejoin").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/rooms/**/report-card").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/rooms/**/preview-questions").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health/liveness").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/info").permitAll()
                    .requestMatchers("/error").permitAll()
                    .anyRequest().authenticated()
            )
            .addFilterBefore(playerTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response,
                            int status,
                            String code,
                            String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.builder()
                .code(code)
                .message(message)
                .requestId(MDC.get("requestId"))
                .build());
    }
}
