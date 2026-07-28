package com.aquatrack.smartwaterbilling.dto.purchase;

import com.aquatrack.smartwaterbilling.entity.enums.PurchaseSource;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for POST /api/purchases.
 */
@Data
public class PurchaseRequest {

    @NotNull(message = "apartmentId is required")
    private Long apartmentId;

    @NotNull(message = "cycleId is required")
    private Long cycleId;

    @NotNull(message = "volumePurchasedKl is required")
    @DecimalMin(value = "0.001", message = "volumePurchasedKl must be > 0")
    private BigDecimal volumePurchasedKl;

    @NotNull(message = "unitCost is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "unitCost must be >= 0")
    private BigDecimal unitCost;

    @NotNull(message = "purchaseDate is required")
    private LocalDate purchaseDate;

    @NotNull(message = "source is required")
    private PurchaseSource source;
}
