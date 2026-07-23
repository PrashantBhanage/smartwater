package com.aquatrack.smartwaterbilling.dto.household;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response body for household endpoints.
 */
@Data
@Builder
public class HouseholdResponse {

    private Long id;
    private Long apartmentId;
    private String apartmentName;
    private String flatNumber;
    private BigDecimal areaSqft;
    private Integer occupancyCount;
    private Boolean hasMeter;
    private BigDecimal dailyThresholdLiters;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
