package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.entity.TariffPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TariffCalculationServiceTest {

    private TariffCalculationService service;
    private TariffPlan plan;

    @BeforeEach
    void setUp() {
        // We can pass null repositories for pure mathematical testing of calculateBillWithPlan method
        service = new TariffCalculationService(null, null);
        plan = TariffPlan.builder()
                .tier1LimitKl(BigDecimal.valueOf(15.000)) // 15 kL limit
                .tier1Rate(BigDecimal.valueOf(20.0000))   // $20 per kL
                .tier2Rate(BigDecimal.valueOf(35.0000))   // $35 per kL
                .effectiveFromDate(LocalDate.of(2024, 1, 1))
                .build();
    }

    @Test
    @DisplayName("Calculate Bill - Zero Usage")
    void testZeroUsage() {
        BigDecimal result = service.calculateBillWithPlan(BigDecimal.ZERO, plan);
        assertEquals(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), result);
    }

    @Test
    @DisplayName("Calculate Bill - Null Usage")
    void testNullUsage() {
        BigDecimal result = service.calculateBillWithPlan(null, plan);
        assertEquals(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), result);
    }

    @Test
    @DisplayName("Calculate Bill - Usage under Tier 1 limit (e.g. 5000 Liters = 5 kL)")
    void testUsageUnderLimit() {
        BigDecimal result = service.calculateBillWithPlan(BigDecimal.valueOf(5000), plan);
        // 5 * 20 = 100.00
        assertEquals(BigDecimal.valueOf(100.00).setScale(2, RoundingMode.HALF_UP), result);
    }

    @Test
    @DisplayName("Calculate Bill - Usage exactly at Tier 1 limit (e.g. 15000 Liters = 15 kL)")
    void testUsageAtLimit() {
        BigDecimal result = service.calculateBillWithPlan(BigDecimal.valueOf(15000), plan);
        // 15 * 20 = 300.00
        assertEquals(BigDecimal.valueOf(300.00).setScale(2, RoundingMode.HALF_UP), result);
    }

    @Test
    @DisplayName("Calculate Bill - Usage over Tier 1 limit (e.g. 20000 Liters = 20 kL)")
    void testUsageOverLimit() {
        BigDecimal result = service.calculateBillWithPlan(BigDecimal.valueOf(20000), plan);
        // 15 * 20 + 5 * 35 = 300 + 175 = 475.00
        assertEquals(BigDecimal.valueOf(475.00).setScale(2, RoundingMode.HALF_UP), result);
    }

    @Test
    @DisplayName("Calculate Bill - Large Usage (e.g. 100,000 Liters = 100 kL)")
    void testLargeUsage() {
        BigDecimal result = service.calculateBillWithPlan(BigDecimal.valueOf(100000), plan);
        // 15 * 20 + 85 * 35 = 300 + 2975 = 3275.00
        assertEquals(BigDecimal.valueOf(3275.00).setScale(2, RoundingMode.HALF_UP), result);
    }
}
