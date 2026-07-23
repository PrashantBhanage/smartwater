package com.aquatrack.smartwaterbilling.controller;

import com.aquatrack.smartwaterbilling.dto.auth.*;
import com.aquatrack.smartwaterbilling.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Authentication endpoints — register, login, and profile management.
 * /api/auth/register and /api/auth/login are public (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new user (ADMIN or RESIDENT).
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * Authenticate and receive a JWT token.
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Get the profile of the currently authenticated user.
     * GET /api/auth/me
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileDto> getProfile(Principal principal) {
        return ResponseEntity.ok(authService.getProfile(principal.getName()));
    }

    /**
     * Partial update of the authenticated user's own profile.
     * PATCH /api/auth/me
     */
    @PatchMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileDto> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Principal principal) {
        return ResponseEntity.ok(authService.updateProfile(principal.getName(), request));
    }
}
