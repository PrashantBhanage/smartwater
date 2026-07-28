package com.aquatrack.smartwaterbilling.dto.purchase;

import com.aquatrack.smartwaterbilling.entity.enums.PurchaseSource;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class PurchaseResponse {

    private Long id;
    private Long apartmentId;
    private Long cycleId;
    private BigDecimal volumePurchasedKl;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private LocalDate purchaseDate;
    private PurchaseSource source;
    private LocalDateTime createdAt;
}
