package com.verdikt.verdikt_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.allowed-origins}")
    private String[] allowedOrigins;

    @PostConstruct
    void validateOrigins() {
        allowedOrigins = Arrays.stream(allowedOrigins)
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);
        if (allowedOrigins.length == 0 || Arrays.asList(allowedOrigins).contains("*")) {
            throw new IllegalStateException("APP_ALLOWED_ORIGINS must contain explicit origins.");
        }
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-Player-Token", "Authorization", "X-Request-ID", "X-Trace-ID"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/ws/**", config);

        CorsConfiguration actuatorConfig = new CorsConfiguration();
        actuatorConfig.setAllowedOrigins(List.of(allowedOrigins));
        actuatorConfig.setAllowedMethods(List.of("GET", "OPTIONS"));
        actuatorConfig.setAllowedHeaders(List.of("Content-Type"));
        actuatorConfig.setAllowCredentials(false);
        actuatorConfig.setMaxAge(3600L);
        source.registerCorsConfiguration("/actuator/**", actuatorConfig);

        return new CorsFilter(source);
    }
}
