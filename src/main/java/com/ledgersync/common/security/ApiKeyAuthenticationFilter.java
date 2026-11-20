package com.ledgersync.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final String headerName;
    private final String ingestionKey;
    private final String operatorKey;
    private final String adminKey;
    private final boolean securityEnabled;

    public ApiKeyAuthenticationFilter(
            @Value("${ledgersync.security.header-name:X-API-Key}") String headerName,
            @Value("${ledgersync.security.api-keys.ingestion:}") String ingestionKey,
            @Value("${ledgersync.security.api-keys.operator:}") String operatorKey,
            @Value("${ledgersync.security.api-keys.admin:}") String adminKey,
            @Value("${ledgersync.security.enabled:true}") boolean securityEnabled) {
        this.headerName = headerName;
        this.ingestionKey = ingestionKey;
        this.operatorKey = operatorKey;
        this.adminKey = adminKey;
        this.securityEnabled = securityEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!securityEnabled) {
            SecurityContextHolder.getContext().setAuthentication(
                    new ApiKeyAuthenticationToken("bypass", "dev-admin",
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OPERATOR"))));
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(headerName);
        if (apiKey != null && !apiKey.isBlank()) {
            if (apiKey.equals(adminKey)) {
                SecurityContextHolder.getContext().setAuthentication(
                        new ApiKeyAuthenticationToken(apiKey, "admin",
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OPERATOR"))));
            } else if (apiKey.equals(operatorKey)) {
                SecurityContextHolder.getContext().setAuthentication(
                        new ApiKeyAuthenticationToken(apiKey, "operator",
                                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))));
            } else if (apiKey.equals(ingestionKey)) {
                SecurityContextHolder.getContext().setAuthentication(
                        new ApiKeyAuthenticationToken(apiKey, "ingestion-client",
                                List.of(new SimpleGrantedAuthority("ROLE_INGESTION"))));
            }
        }

        filterChain.doFilter(request, response);
    }
}
