package com.aquatrack.smartwaterbilling.dto.billing;

import com.aquatrack.smartwaterbilling.entity.enums.BillingStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class BillingCycleResponse {

    private Long id;
    private Long apartmentId;
    private LocalDate cycleStartDate;
    private LocalDate cycleEndDate;
    private BillingStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** Populated on finalize — number of invoices generated. */
    private Integer invoicesGenerated;
}
