package com.aquatrack.smartwaterbilling.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for PATCH /api/auth/me — all fields optional (partial update).
 */
@Data
public class UpdateProfileRequest {

    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;

    @Email(message = "Email must be a valid email address")
    private String email;

    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
