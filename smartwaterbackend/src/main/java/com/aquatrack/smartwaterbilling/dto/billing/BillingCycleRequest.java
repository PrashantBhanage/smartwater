package com.aquatrack.smartwaterbilling.dto.billing;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * Request body for POST /api/billing-cycles (open a new cycle).
 */
@Data
public class BillingCycleRequest {

    @NotNull(message = "apartmentId is required")
    private Long apartmentId;

    @NotNull(message = "cycleStartDate is required")
    private LocalDate cycleStartDate;

    @NotNull(message = "cycleEndDate is required")
    private LocalDate cycleEndDate;
}
