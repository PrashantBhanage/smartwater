package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.entity.Household;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CostDistributionService — shared cost apportionment")
class CostDistributionServiceTest {

    private CostDistributionService service;

    @BeforeEach
    void setUp() {
        service = new CostDistributionService();
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private static Household metered(long id) {
        return Household.builder().id(id).hasMeter(true).flatNumber("M-" + id)
                .occupancyCount(2).build();
    }

    private static Household unmetered(long id, String area) {
        return Household.builder().id(id).hasMeter(false).flatNumber("U-" + id)
                .areaSqft(area == null ? null : new BigDecimal(area))
                .occupancyCount(2).build();
    }

    private static void assertSumEquals(Map<Long, BigDecimal> shares, String expected) {
        BigDecimal sum = shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(expected);
    }

    // ----------------------------------------------------------------
    // Zero / empty
    // ----------------------------------------------------------------

    @Test
    @DisplayName("empty household list → empty map")
    void emptyHouseholds() {
        assertThat(service.distribute(List.of(), Map.of(), new BigDecimal("100"))).isEmpty();
    }

    @Test
    @DisplayName("zero shared cost → all zeros")
    void zeroSharedCost() {
        List<Household> hh = List.of(metered(1), metered(2));
        Map<Long, BigDecimal> result = service.distribute(hh, Map.of(1L, BigDecimal.TEN), BigDecimal.ZERO);
        assertThat(result.get(1L)).isEqualByComparingTo("0.00");
        assertThat(result.get(2L)).isEqualByComparingTo("0.00");
    }

    // ----------------------------------------------------------------
    // All metered — proportional to usage
    // ----------------------------------------------------------------

    @Test
    @DisplayName("all-metered: proportional to consumption")
    void allMetered_proportional() {
        List<Household> hh = List.of(metered(1), metered(2));
        // HH1 used 300 L, HH2 used 100 L → 75% / 25% of 100
        Map<Long, BigDecimal> usage = Map.of(
                1L, new BigDecimal("300"),
                2L, new BigDecimal("100"));
        Map<Long, BigDecimal> result = service.distribute(hh, usage, new BigDecimal("100.00"));

        assertThat(result.get(1L)).isEqualByComparingTo("75.00");
        assertThat(result.get(2L)).isEqualByComparingTo("25.00");
        assertSumEquals(result, "100.00");
    }

    @Test
    @DisplayName("all-metered with zero usage → equal split")
    void allMetered_zeroUsage_equalSplit() {
        List<Household> hh = List.of(metered(1), metered(2), metered(3));
        Map<Long, BigDecimal> result = service.distribute(
                hh, Map.of(), new BigDecimal("90.00"));

        assertThat(result.get(1L)).isEqualByComparingTo("30.00");
        assertThat(result.get(2L)).isEqualByComparingTo("30.00");
        assertThat(result.get(3L)).isEqualByComparingTo("30.00");
        assertSumEquals(result, "90.00");
    }

    // ----------------------------------------------------------------
    // All unmetered — area-based (required edge case)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("all-unmetered: proportional to area_sqft")
    void allUnmetered_areaSplit() {
        List<Household> hh = List.of(
                unmetered(1, "1000"),
                unmetered(2, "3000"));
        Map<Long, BigDecimal> result = service.distribute(
                hh, Map.of(), new BigDecimal("400.00"));

        // 1000/4000 = 25%, 3000/4000 = 75%
        assertThat(result.get(1L)).isEqualByComparingTo("100.00");
        assertThat(result.get(2L)).isEqualByComparingTo("300.00");
        assertSumEquals(result, "400.00");
    }

    @Test
    @DisplayName("all-unmetered with null/zero area → equal split")
    void allUnmetered_noArea_equalSplit() {
        List<Household> hh = List.of(unmetered(1, null), unmetered(2, "0"));
        Map<Long, BigDecimal> result = service.distribute(
                hh, Map.of(), new BigDecimal("50.00"));

        assertThat(result.get(1L)).isEqualByComparingTo("25.00");
        assertThat(result.get(2L)).isEqualByComparingTo("25.00");
        assertSumEquals(result, "50.00");
    }

    // ----------------------------------------------------------------
    // Mixed
    // ----------------------------------------------------------------

    @Test
    @DisplayName("mixed: headcount pools then usage/area within each group")
    void mixed_headcountThenRules() {
        // 2 metered + 2 unmetered → each pool gets 50% of 200 = 100
        List<Household> hh = List.of(
                metered(1), metered(2),
                unmetered(3, "100"), unmetered(4, "300"));
        Map<Long, BigDecimal> usage = Map.of(
                1L, new BigDecimal("75"),
                2L, new BigDecimal("25"));

        Map<Long, BigDecimal> result = service.distribute(hh, usage, new BigDecimal("200.00"));

        // Metered pool 100: 75% → 75, 25% → 25
        assertThat(result.get(1L)).isEqualByComparingTo("75.00");
        assertThat(result.get(2L)).isEqualByComparingTo("25.00");
        // Unmetered pool 100: 25% → 25, 75% → 75
        assertThat(result.get(3L)).isEqualByComparingTo("25.00");
        assertThat(result.get(4L)).isEqualByComparingTo("75.00");
        assertSumEquals(result, "200.00");
    }
}
