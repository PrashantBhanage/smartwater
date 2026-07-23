package com.aquatrack.smartwaterbilling.dto.household;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for POST /api/households/{id}/assign-resident
 */
@Data
public class AssignResidentRequest {

    @NotBlank(message = "Resident email is required")
    @Email(message = "Must be a valid email address")
    private String residentEmail;
}
