package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.entity.TariffPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TariffBillingEngine}.
 *
 * <p>Default plan used unless noted:
 * <ul>
 *   <li>tier1LimitKl = 10 kL</li>
 *   <li>tier1Rate    = 20.0000 / kL</li>
 *   <li>tier2Rate    = 35.0000 / kL</li>
 * </ul>
 */
@DisplayName("TariffBillingEngine — tiered charge calculation")
class TariffBillingEngineTest {

    private TariffBillingEngine engine;
    private TariffPlan plan;

    @BeforeEach
    void setUp() {
        engine = new TariffBillingEngine();
        plan = TariffPlan.builder()
                .tier1LimitKl(new BigDecimal("10.000"))
                .tier1Rate(new BigDecimal("20.0000"))
                .tier2Rate(new BigDecimal("35.0000"))
                .build();
    }

    @Test
    @DisplayName("null volume → 0.00")
    void nullVolume_returnsZero() {
        assertThat(engine.calculateCharge(null, plan))
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("zero usage → 0.00")
    void zeroUsage_returnsZero() {
        assertThat(engine.calculateCharge(BigDecimal.ZERO, plan))
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("negative usage → 0.00")
    void negativeUsage_returnsZero() {
        assertThat(engine.calculateCharge(new BigDecimal("-100"), plan))
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("null plan → IllegalArgumentException")
    void nullPlan_throws() {
        assertThatThrownBy(() -> engine.calculateCharge(BigDecimal.valueOf(1000), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tariff plan");
    }

    @Test
    @DisplayName("usage exactly at tier1 boundary (10 kL = 10000 L) → all tier1")
    void exactlyAtTierBoundary_allTier1() {
        // 10 kL × 20 = 200.00
        BigDecimal charge = engine.calculateCharge(new BigDecimal("10000"), plan);
        assertThat(charge).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("usage just below tier1 boundary → all tier1")
    void justBelowBoundary_allTier1() {
        // 9.999 kL × 20 = 199.98
        BigDecimal charge = engine.calculateCharge(new BigDecimal("9999"), plan);
        assertThat(charge).isEqualByComparingTo("199.98");
    }

    @Test
    @DisplayName("usage just above tier1 boundary → tier1 + tier2 excess")
    void justAboveBoundary_usesTier2() {
        // 10.001 kL → 10×20 + 0.001×35 = 200 + 0.035 = 200.035 → 200.04
        BigDecimal charge = engine.calculateCharge(new BigDecimal("10001"), plan);
        assertThat(charge).isEqualByComparingTo("200.04");
    }

    @Test
    @DisplayName("usage entirely in tier2 region")
    void deepIntoTier2() {
        // 25 kL → 10×20 + 15×35 = 200 + 525 = 725.00
        BigDecimal charge = engine.calculateCharge(new BigDecimal("25000"), plan);
        assertThat(charge).isEqualByComparingTo("725.00");
    }

    @ParameterizedTest(name = "{0} L → charge {1}")
    @CsvSource({
            "0,      0.00",
            "500,    10.00",   // 0.5 kL × 20
            "1000,   20.00",   // 1 kL × 20
            "10000, 200.00",   // exact boundary
            "15000, 375.00",   // 10×20 + 5×35 = 200+175
            "20000, 550.00"    // 10×20 + 10×35 = 200+350
    })
    @DisplayName("parameterized volume → expected charge")
    void parameterizedCharges(String liters, String expected) {
        assertThat(engine.calculateCharge(new BigDecimal(liters), plan))
                .isEqualByComparingTo(expected);
    }
}
