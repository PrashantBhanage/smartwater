package com.aquatrack.smartwaterbilling.dto.tariff;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TariffPlanUpdateRequest {

    @DecimalMin(value = "0.001", message = "tier1LimitKl must be > 0")
    private BigDecimal tier1LimitKl;

    @DecimalMin(value = "0.0", inclusive = true, message = "tier1Rate must be >= 0")
    private BigDecimal tier1Rate;

    @DecimalMin(value = "0.0", inclusive = true, message = "tier2Rate must be >= 0")
    private BigDecimal tier2Rate;

    private LocalDate effectiveFromDate;
}
