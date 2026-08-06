package com.aquatrack.smartwaterbilling.dto.alert;

import com.aquatrack.smartwaterbilling.entity.enums.AlertSeverity;
import com.aquatrack.smartwaterbilling.entity.enums.AlertType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class AlertResponse {

    private Long id;
    private Long householdId;
    private String flatNumber;
    private AlertType alertType;
    private AlertSeverity severity;
    private String message;
    private BigDecimal usageLiters;
    private LocalDate readingDate;
    private Boolean acknowledged;
    private LocalDateTime createdAt;
}
