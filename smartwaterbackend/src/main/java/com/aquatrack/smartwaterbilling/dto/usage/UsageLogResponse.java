package com.aquatrack.smartwaterbilling.dto.usage;

import com.aquatrack.smartwaterbilling.entity.enums.UsageSource;
import com.aquatrack.smartwaterbilling.entity.enums.UsageStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response body for usage log endpoints.
 * The {@code usageStatus} field enables the frontend (Module 3) to
 * directly render the colour indicator without any recalculation.
 */
@Data
@Builder
public class UsageLogResponse {

    private Long id;
    private Long householdId;
    private String flatNumber;
    private Long apartmentId;
    private LocalDate readingDate;
    private BigDecimal meterReadingValue;
    private BigDecimal volumeUsedLiters;
    private BigDecimal dailyThresholdLiters;
    private UsageSource source;

    /**
     * Colour-coded status: GREEN / YELLOW / RED.
     * Stored at creation time; does NOT change when threshold is updated.
     */
    private UsageStatus usageStatus;

    private LocalDateTime createdAt;
}
