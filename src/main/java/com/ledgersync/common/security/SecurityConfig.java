package com.ledgersync.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Set<String> CSRF_PROTECTED_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ApiKeyAuthenticationFilter apiKeyFilter;
    private final String allowedOrigins;

    public SecurityConfig(
            ApiKeyAuthenticationFilter apiKeyFilter,
            @Value("${ledgersync.security.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOrigins) {
        this.apiKeyFilter = apiKeyFilter;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(request ->
                                CSRF_PROTECTED_METHODS.contains(request.getMethod())
                                        && !request.getRequestURI().startsWith("/api/v1/webhooks/")
                                        && request.getHeader("X-API-Key") == null))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        // Public actuator, docs, and health checks
                        .requestMatchers("/actuator/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // Webhooks are verified using cryptographic signatures directly
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/**").permitAll()
                        // Public session check / login
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Destructive demo reset is restricted to administrators
                        .requestMatchers(HttpMethod.POST, "/api/v1/finance/imports/demo/reset").hasRole("ADMIN")
                        // Discrepancy resolution and admin actions
                        .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/discrepancies/*/resolve").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/queue/redrive/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/run").hasAnyRole("OPERATOR", "ADMIN")
                        // Finance Reconciliation 2.0 endpoints
                        .requestMatchers("/api/v1/finance/**").hasAnyRole("OPERATOR", "ADMIN")
                        // General read operations
                        .requestMatchers("/api/v1/**").hasAnyRole("OPERATOR", "ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split("\\s*,\\s*")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
