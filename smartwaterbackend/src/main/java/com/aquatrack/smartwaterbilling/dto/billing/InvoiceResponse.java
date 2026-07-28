package com.aquatrack.smartwaterbilling.dto.billing;

import com.aquatrack.smartwaterbilling.entity.enums.InvoiceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class InvoiceResponse {

    private Long id;
    private Long householdId;
    private String flatNumber;
    private Long cycleId;
    private BigDecimal baseCharge;
    private BigDecimal sharedAllocation;
    private BigDecimal adjustments;
    private BigDecimal totalAmount;
    private InvoiceStatus status;
    private LocalDateTime createdAt;
}
