package com.example.baas.sandbox.security;

import com.example.baas.sandbox.api.ErrorEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BaasContextFilter extends OncePerRequestFilter {

    public static final String PARTNER_ID = "X-Partner-Id";
    public static final String CUSTOMER_ID = "X-On-Behalf-Of-Customer-Id";
    public static final String CONSENT_ID = "X-Customer-Consent-Id";

    private static final List<String> REQUIRED_HEADERS = List.of(PARTNER_ID, CUSTOMER_ID, CONSENT_ID);

    private final ObjectMapper objectMapper;

    public BaasContextFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/payments");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        List<String> missingHeaders = REQUIRED_HEADERS.stream()
                .filter(header -> isBlank(request.getHeader(header)))
                .toList();

        if (!missingHeaders.isEmpty()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ErrorEnvelope.of(
                    "BAAS_CONTEXT_REQUIRED",
                    "BaaS requester context headers are required",
                    Map.of("missingHeaders", missingHeaders)));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
