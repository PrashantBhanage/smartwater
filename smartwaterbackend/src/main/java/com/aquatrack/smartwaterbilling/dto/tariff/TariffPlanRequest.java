package com.aquatrack.smartwaterbilling.dto.tariff;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for POST /api/tariff-plans.
 */
@Data
public class TariffPlanRequest {

    @NotNull(message = "apartmentId is required")
    private Long apartmentId;

    @NotNull(message = "tier1LimitKl is required")
    @DecimalMin(value = "0.001", message = "tier1LimitKl must be > 0")
    private BigDecimal tier1LimitKl;

    @NotNull(message = "tier1Rate is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "tier1Rate must be >= 0")
    private BigDecimal tier1Rate;

    @NotNull(message = "tier2Rate is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "tier2Rate must be >= 0")
    private BigDecimal tier2Rate;

    @NotNull(message = "effectiveFromDate is required")
    private LocalDate effectiveFromDate;
}
