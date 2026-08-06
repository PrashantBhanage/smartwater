package com.aquatrack.smartwaterbilling.dto.purchase;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class BulkPurchaseResponse {
    private Long id;
    private Long apartmentId;
    private LocalDate purchaseDate;
    private BigDecimal volumeLiters;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
