package com.aquatrack.smartwaterbilling.dto.billing;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BillingCycleInvoiceResponse {
    private Long id;
    private Long householdId;
    private String flatNumber;
    private Long billingCycleId;
    private BigDecimal baseCharge;
    private BigDecimal sharedCostAllocation;
    private BigDecimal totalCharge;
    private String paidStatus;
    private LocalDateTime createdAt;
}
