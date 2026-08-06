package com.aquatrack.smartwaterbilling.dto.billing;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class InvoiceSummaryResponse {
    private Integer invoicesGenerated;
    private BigDecimal totalBaseCharge;
    private BigDecimal totalSharedAllocation;
    private BigDecimal totalAmount;
}
