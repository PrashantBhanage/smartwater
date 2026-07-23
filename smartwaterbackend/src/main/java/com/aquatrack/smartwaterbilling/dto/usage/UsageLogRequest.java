package com.aquatrack.smartwaterbilling.dto.usage;

import com.aquatrack.smartwaterbilling.entity.enums.UsageSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for POST /api/usage-logs (manual entry).
 * All CSV rows are also deserialized into this structure before processing.
 */
@Data
public class UsageLogRequest {

    @NotNull(message = "Household ID is required")
    private Long householdId;

    @NotNull(message = "Reading date is required")
    @PastOrPresent(message = "Reading date cannot be in the future")
    private LocalDate readingDate;

    /** Optional cumulative meter reading. */
    @PositiveOrZero(message = "Meter reading value must be zero or positive")
    private BigDecimal meterReadingValue;

    @NotNull(message = "Volume used (in litres) is required")
    @PositiveOrZero(message = "Volume used must be zero or positive")
    private BigDecimal volumeUsedLiters;

    @NotNull(message = "Source is required (MANUAL or CSV_UPLOAD)")
    private UsageSource source;
}
