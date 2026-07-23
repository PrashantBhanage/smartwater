package com.aquatrack.smartwaterbilling.dto.auth;

import com.aquatrack.smartwaterbilling.entity.enums.Role;
import lombok.Builder;
import lombok.Data;

/**
 * Response for /api/auth/login and /api/auth/register containing the JWT token.
 */
@Data
@Builder
public class AuthResponse {

    private String token;
    private String tokenType;
    private Long expiresInMs;

    // User info embedded for convenience
    private Long userId;
    private String email;
    private String fullName;
    private Role role;
}
