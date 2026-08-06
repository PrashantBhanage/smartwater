package com.aquatrack.smartwaterbilling.dto.purchase;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class BulkPurchaseSummaryResponse {
    private BigDecimal totalVolumeLiters;
    private BigDecimal totalCost;
    private List<BulkPurchaseResponse> purchases;
}
