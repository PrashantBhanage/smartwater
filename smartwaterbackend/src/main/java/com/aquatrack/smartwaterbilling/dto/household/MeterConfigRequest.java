package com.aquatrack.smartwaterbilling.dto.household;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * Request body for PATCH /api/households/{id}/meter-config
 */
@Data
public class MeterConfigRequest {

    @NotNull(message = "hasMeter flag is required")
    private Boolean hasMeter;

    /**
     * New daily threshold in litres (optional — only update if provided).
     */
    @Positive(message = "Daily threshold must be a positive number")
    private java.math.BigDecimal dailyThresholdLiters;
}
