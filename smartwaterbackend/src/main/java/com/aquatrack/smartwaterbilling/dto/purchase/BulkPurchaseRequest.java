package com.aquatrack.smartwaterbilling.dto.purchase;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BulkPurchaseRequest {

    @NotNull(message = "purchaseDate is required")
    private LocalDate purchaseDate;

    @NotNull(message = "volumeLiters is required")
    @DecimalMin(value = "0.01", message = "volumeLiters must be > 0")
    private BigDecimal volumeLiters;

    @NotNull(message = "unitCost is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "unitCost must be >= 0")
    private BigDecimal unitCost;
}
