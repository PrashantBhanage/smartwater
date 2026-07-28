package com.aquatrack.smartwaterbilling.dto.tariff;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class TariffPlanResponse {

    private Long id;
    private Long apartmentId;
    private BigDecimal tier1LimitKl;
    private BigDecimal tier1Rate;
    private BigDecimal tier2Rate;
    private LocalDate effectiveFromDate;
    private LocalDateTime createdAt;
}
