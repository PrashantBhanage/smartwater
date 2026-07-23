package com.aquatrack.smartwaterbilling.dto.household;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for POST /api/households
 */
@Data
public class HouseholdRequest {

    @NotNull(message = "Apartment ID is required")
    private Long apartmentId;

    @NotBlank(message = "Flat number is required")
    @Size(max = 50, message = "Flat number must not exceed 50 characters")
    private String flatNumber;

    @Positive(message = "Area must be a positive number")
    private BigDecimal areaSqft;

    @NotNull(message = "Occupancy count is required")
    @PositiveOrZero(message = "Occupancy count must be zero or positive")
    private Integer occupancyCount;

    @NotNull(message = "Has meter flag is required")
    private Boolean hasMeter;

    /**
     * Optional: defaults to 500 litres/day if not provided.
     */
    @Positive(message = "Daily threshold must be a positive number")
    private BigDecimal dailyThresholdLiters;
}
