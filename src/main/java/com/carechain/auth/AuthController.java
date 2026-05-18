package com.carechain.auth;

import com.carechain.auth.model.AuthResponse;
import com.carechain.auth.model.LoginRequest;
import com.carechain.auth.model.RegisterRequest;
import com.carechain.config.ApiErrorException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private AuthCookieService authCookieService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request,
                                      HttpServletResponse response) {
        AuthResponse authResponse = authService.register(request);
        authCookieService.addAuthCookie(response, authResponse.getToken());
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);
        authCookieService.addAuthCookie(response, authResponse.getToken());
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        authCookieService.clearAuthCookie(response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        boolean anonymous = authentication == null
                || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ANONYMOUS".equals(authority.getAuthority()));

        if (anonymous) {
            throw ApiErrorException.unauthorized("Unauthorized");
        }

        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .orElse("");

        return ResponseEntity.ok(Map.of(
                "email", authentication.getName(),
                "role", role
        ));
    }
}
