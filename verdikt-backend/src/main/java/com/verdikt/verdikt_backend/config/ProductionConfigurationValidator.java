package com.verdikt.verdikt_backend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionConfigurationValidator {

    @Value("${SPRING_DATASOURCE_PASSWORD}")
    private String databasePassword;

    @Value("${APP_TOKEN_SECRET}")
    private String tokenSecret;

    @Value("${APP_ALLOWED_ORIGINS}")
    private String allowedOrigins;

    @PostConstruct
    void validate() {
        if (isWeak(databasePassword) || databasePassword.length() < 12) {
            throw new IllegalStateException("SPRING_DATASOURCE_PASSWORD must be a strong production secret.");
        }
        if (isWeak(tokenSecret) || tokenSecret.length() < 32) {
            throw new IllegalStateException("APP_TOKEN_SECRET must be at least 32 characters in production.");
        }
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
        if (origins.length == 0 || Arrays.stream(origins).anyMatch(value -> "*".equals(value)
                || !(value.startsWith("https://") || value.startsWith("http://")))) {
            throw new IllegalStateException("APP_ALLOWED_ORIGINS must contain explicit http(s) origins.");
        }
    }

    private boolean isWeak(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank()
                || normalized.contains("summie")
                || normalized.contains("change-me")
                || normalized.contains("replace-with");
    }
}
