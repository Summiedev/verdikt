package com.verdikt.verdikt_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_KEY = "requestId";
    private static final String TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveOrGenerate(request.getHeader("X-Request-ID"));
        String traceId = resolveOrGenerate(request.getHeader("X-Trace-ID"));

        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader("X-Request-ID", requestId);
        response.setHeader("X-Trace-ID", traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private String resolveOrGenerate(String value) {
        if (value != null && value.length() <= 64 && value.matches("[A-Za-z0-9._-]+")) {
            return value;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
