package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.entity.Apartment;
import com.aquatrack.smartwaterbilling.entity.BillingCycle;
import com.aquatrack.smartwaterbilling.entity.Household;
import com.aquatrack.smartwaterbilling.entity.WaterUsageLog;
import com.aquatrack.smartwaterbilling.repository.HouseholdRepository;
import com.aquatrack.smartwaterbilling.repository.WaterUsageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class CostDistributionServiceMilestone2Test {

    private CostDistributionService service;
    private HouseholdRepository householdRepository;
    private WaterUsageLogRepository usageLogRepository;

    private Apartment apartment;
    private BillingCycle cycle;
    private Household h1;
    private Household h2;
    private Household h3;

    @BeforeEach
    void setUp() {
        householdRepository = Mockito.mock(HouseholdRepository.class);
        usageLogRepository = Mockito.mock(WaterUsageLogRepository.class);
        service = new CostDistributionService(householdRepository, usageLogRepository);

        apartment = Apartment.builder().id(1L).name("Test Apartment").build();
        cycle = BillingCycle.builder()
                .id(1L)
                .apartment(apartment)
                .cycleStartDate(LocalDate.of(2024, 6, 1))
                .cycleEndDate(LocalDate.of(2024, 6, 30))
                .build();

        h1 = Household.builder().id(101L).flatNumber("101").areaSqft(BigDecimal.valueOf(1000)).build();
        h2 = Household.builder().id(102L).flatNumber("102").areaSqft(BigDecimal.valueOf(1500)).build();
        h3 = Household.builder().id(103L).flatNumber("103").areaSqft(BigDecimal.valueOf(2500)).build();

        when(householdRepository.findAllByApartmentId(1L)).thenReturn(List.of(h1, h2, h3));
    }

    @Test
    @DisplayName("Proportional distribution based on usage")
    void testProportionalDistributionByUsage() {
        // Setup usage logs
        WaterUsageLog log1 = WaterUsageLog.builder().volumeUsedLiters(BigDecimal.valueOf(2000)).build();
        WaterUsageLog log2 = WaterUsageLog.builder().volumeUsedLiters(BigDecimal.valueOf(3000)).build();
        WaterUsageLog log3 = WaterUsageLog.builder().volumeUsedLiters(BigDecimal.valueOf(5000)).build();

        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(101L, cycle.getCycleStartDate(), cycle.getCycleEndDate()))
                .thenReturn(List.of(log1));
        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(102L, cycle.getCycleStartDate(), cycle.getCycleEndDate()))
                .thenReturn(List.of(log2));
        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(103L, cycle.getCycleStartDate(), cycle.getCycleEndDate()))
                .thenReturn(List.of(log3));

        // Distribute $1000
        Map<Household, BigDecimal> distribution = service.distributeApartmentCost(apartment, cycle, BigDecimal.valueOf(1000.00));

        // Total usage = 2000 + 3000 + 5000 = 10000 Liters
        // h1 = (2000/10000) * 1000 = $200
        // h2 = (3000/10000) * 1000 = $300
        // h3 = (5000/10000) * 1000 = $500
        assertEquals(BigDecimal.valueOf(200.00).setScale(2, RoundingMode.HALF_UP), distribution.get(h1));
        assertEquals(BigDecimal.valueOf(300.00).setScale(2, RoundingMode.HALF_UP), distribution.get(h2));
        assertEquals(BigDecimal.valueOf(500.00).setScale(2, RoundingMode.HALF_UP), distribution.get(h3));
    }

    @Test
    @DisplayName("Fallback to flat area distribution if no usage data")
    void testFallbackToAreaDistribution() {
        // No usage logs for any household
        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(Mockito.anyLong(), Mockito.any(), Mockito.any()))
                .thenReturn(List.of());

        // Distribute $1000
        Map<Household, BigDecimal> distribution = service.distributeApartmentCost(apartment, cycle, BigDecimal.valueOf(1000.00));

        // Total area = 1000 + 1500 + 2500 = 5000 sqft
        // h1 = (1000/5000) * 1000 = $200
        // h2 = (1500/5000) * 1000 = $300
        // h3 = (2500/5000) * 1000 = $500
        assertEquals(BigDecimal.valueOf(200.00).setScale(2, RoundingMode.HALF_UP), distribution.get(h1));
        assertEquals(BigDecimal.valueOf(300.00).setScale(2, RoundingMode.HALF_UP), distribution.get(h2));
        assertEquals(BigDecimal.valueOf(500.00).setScale(2, RoundingMode.HALF_UP), distribution.get(h3));
    }

    @Test
    @DisplayName("Even distribution fallback if no usage and no area data")
    void testEvenDistributionFallback() {
        // Setup households with zero area
        h1.setAreaSqft(BigDecimal.ZERO);
        h2.setAreaSqft(BigDecimal.ZERO);
        h3.setAreaSqft(BigDecimal.ZERO);

        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(Mockito.anyLong(), Mockito.any(), Mockito.any()))
                .thenReturn(List.of());

        // Distribute $900
        Map<Household, BigDecimal> distribution = service.distributeApartmentCost(apartment, cycle, BigDecimal.valueOf(900.00));

        // Split even: 900 / 3 = $300 each
        assertEquals(BigDecimal.valueOf(300.00).setScale(2, RoundingMode.HALF_UP), distribution.get(h1));
        assertEquals(BigDecimal.valueOf(300.00).setScale(2, RoundingMode.HALF_UP), distribution.get(h2));
        assertEquals(BigDecimal.valueOf(300.00).setScale(2, RoundingMode.HALF_UP), distribution.get(h3));
    }

    @Test
    @DisplayName("Even distribution via equal non-zero consumption (usage-proportional branch)")
    void testEvenDistribution_equalConsumption() {
        // All three households consume the same non-zero volume → usage-proportional branch
        WaterUsageLog log1 = WaterUsageLog.builder().volumeUsedLiters(BigDecimal.valueOf(2000)).build();
        WaterUsageLog log2 = WaterUsageLog.builder().volumeUsedLiters(BigDecimal.valueOf(2000)).build();
        WaterUsageLog log3 = WaterUsageLog.builder().volumeUsedLiters(BigDecimal.valueOf(2000)).build();

        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(101L, cycle.getCycleStartDate(), cycle.getCycleEndDate()))
                .thenReturn(List.of(log1));
        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(102L, cycle.getCycleStartDate(), cycle.getCycleEndDate()))
                .thenReturn(List.of(log2));
        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(103L, cycle.getCycleStartDate(), cycle.getCycleEndDate()))
                .thenReturn(List.of(log3));

        // Total usage = 6000 L; each household = 2000/6000 = 1/3 of $900 = $300
        Map<Household, BigDecimal> distribution = service.distributeApartmentCost(apartment, cycle, BigDecimal.valueOf(900.00));

        assertEquals(BigDecimal.valueOf(300.00).setScale(2, RoundingMode.HALF_UP), distribution.get(h1));
        assertEquals(BigDecimal.valueOf(300.00).setScale(2, RoundingMode.HALF_UP), distribution.get(h2));
        assertEquals(BigDecimal.valueOf(300.00).setScale(2, RoundingMode.HALF_UP), distribution.get(h3));

        // Shares must sum exactly to totalApartmentCost
        BigDecimal sum = distribution.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(BigDecimal.valueOf(900.00).setScale(2, RoundingMode.HALF_UP), sum);
    }

    @Test
    @DisplayName("Single household edge case - entire cost goes to it")
    void testSingleHousehold_getsEntireCost() {
        // Only one household in the apartment
        when(householdRepository.findAllByApartmentId(1L)).thenReturn(List.of(h1));

        WaterUsageLog log1 = WaterUsageLog.builder().volumeUsedLiters(BigDecimal.valueOf(5000)).build();
        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(101L, cycle.getCycleStartDate(), cycle.getCycleEndDate()))
                .thenReturn(List.of(log1));

        // Distribute $1000 to a single household with all the usage
        Map<Household, BigDecimal> distribution = service.distributeApartmentCost(apartment, cycle, BigDecimal.valueOf(1000.00));

        assertEquals(BigDecimal.valueOf(1000.00).setScale(2, RoundingMode.HALF_UP), distribution.get(h1));
        assertEquals(1, distribution.size());
    }
}
