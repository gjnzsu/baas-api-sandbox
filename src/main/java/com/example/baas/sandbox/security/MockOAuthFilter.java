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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MockOAuthFilter extends OncePerRequestFilter {

    private static final Map<String, List<String>> TOKENS = Map.of(
            "sandbox-payment-token", List.of("payments:write"),
            "sandbox-readonly-token", List.of("payments:read")
    );

    private final ObjectMapper objectMapper;

    public MockOAuthFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/")
                || path.equals("/index.html")
                || path.equals("/portal.css")
                || path.equals("/portal.js")
                || path.equals("/api/v1/sandbox/scenarios")
                || path.equals("/actuator/health")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Bearer token is required");
            return;
        }
        if (!authorization.startsWith("Bearer ")) {
            writeError(response, HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Bearer token is invalid");
            return;
        }

        String token = authorization.substring("Bearer ".length());
        List<String> scopes = TOKENS.get(token);
        if (scopes == null) {
            writeError(response, HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Bearer token is invalid");
            return;
        }
        if (!scopes.contains("payments:write")) {
            writeError(response, HttpStatus.FORBIDDEN, "INSUFFICIENT_SCOPE", "Token lacks payments:write scope");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                token,
                null,
                scopes.stream().map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ErrorEnvelope.of(code, message, Map.of()));
    }
}
