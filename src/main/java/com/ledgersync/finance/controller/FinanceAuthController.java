package com.ledgersync.finance.controller;

import com.ledgersync.common.security.ApiKeyAuthenticationToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Credential-validated browser sessions")
public class FinanceAuthController {

    private final String operatorUsername;
    private final String operatorPassword;
    private final String adminUsername;
    private final String adminPassword;

    public FinanceAuthController(
            @Value("${ledgersync.security.operator.username}") String operatorUsername,
            @Value("${ledgersync.security.operator.password}") String operatorPassword,
            @Value("${ledgersync.security.admin.username}") String adminUsername,
            @Value("${ledgersync.security.admin.password}") String adminPassword) {
        this.operatorUsername = operatorUsername;
        this.operatorPassword = operatorPassword;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @GetMapping("/session")
    @Operation(summary = "Check the Current Browser Session")
    public ResponseEntity<Map<String, Object>> getSession(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.ok(Map.of("authenticated", false));
        }

        String role = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())) ? "ADMIN" : "OPERATOR";
        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "username", authentication.getName(),
                "role", role
        ));
    }

    @GetMapping("/csrf")
    @Operation(summary = "Issue a CSRF Token for Browser Requests")
    public ResponseEntity<Map<String, String>> getCsrfToken(CsrfToken csrfToken) {
        return ResponseEntity.ok(Map.of("token", csrfToken.getToken()));
    }

    @PostMapping("/login")
    @Operation(summary = "Create an Operator or Administrator Session")
    public ResponseEntity<Map<String, String>> login(
            @RequestBody(required = false) LoginRequest credentials,
            HttpServletRequest request) {
        if (credentials == null || credentials.username() == null) {
            return unauthorized();
        }

        boolean isAdmin = adminUsername.equals(credentials.username())
                && passwordMatches(credentials.password(), adminPassword);
        boolean isOperator = operatorUsername.equals(credentials.username())
                && passwordMatches(credentials.password(), operatorPassword);
        if (!isAdmin && !isOperator) {
            return unauthorized();
        }

        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }

        List<SimpleGrantedAuthority> authorities = isAdmin
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_OPERATOR"))
                : List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        String principal = isAdmin ? adminUsername : operatorUsername;
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new ApiKeyAuthenticationToken("browser-session", principal, authorities));
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "username", principal,
                "role", isAdmin ? "ADMIN" : "OPERATOR"
        ));
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate the Current Browser Session")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    private boolean passwordMatches(String submitted, String expected) {
        return submitted != null && MessageDigest.isEqual(
                submitted.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Invalid username or password"));
    }

    public record LoginRequest(String username, String password) {
    }
}
