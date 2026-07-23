package com.aquatrack.smartwaterbilling.dto.apartment;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * Request body for POST /api/apartments
 */
@Data
public class ApartmentRequest {

    @NotBlank(message = "Apartment name is required")
    private String name;

    @NotBlank(message = "Address is required")
    private String address;

    @NotNull(message = "Total households count is required")
    @Positive(message = "Total households must be a positive number")
    private Integer totalHouseholds;

    @NotBlank(message = "Admin contact is required")
    @Email(message = "Admin contact must be a valid email address")
    private String adminContact;
}
