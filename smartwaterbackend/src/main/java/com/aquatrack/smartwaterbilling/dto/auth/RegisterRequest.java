package com.aquatrack.smartwaterbilling.dto.auth;

import com.aquatrack.smartwaterbilling.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for POST /api/auth/register
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotNull(message = "Role is required (ADMIN or RESIDENT)")
    private Role role;

    /**
     * Required when role = ADMIN.
     * The apartment this admin will manage.
     */
    private Long apartmentId;

    /**
     * Required when role = RESIDENT.
     * The household this resident belongs to.
     */
    private Long householdId;
}
