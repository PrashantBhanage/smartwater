package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.entity.*;
import com.aquatrack.smartwaterbilling.entity.enums.BillingStatus;
import com.aquatrack.smartwaterbilling.exception.DuplicateEntryException;
import com.aquatrack.smartwaterbilling.repository.BillingCycleInvoiceRepository;
import com.aquatrack.smartwaterbilling.repository.BillingCycleRepository;
import com.aquatrack.smartwaterbilling.repository.BulkWaterPurchaseRepository;
import com.aquatrack.smartwaterbilling.repository.HouseholdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InvoiceGenerationService {

    private final BillingCycleInvoiceRepository invoiceRepository;
    private final BillingCycleRepository billingCycleRepository;
    private final HouseholdRepository householdRepository;
    private final BulkWaterPurchaseRepository bulkWaterPurchaseRepository;
    private final TariffCalculationService tariffCalculationService;
    private final CostDistributionService costDistributionService;
    private final AlertService alertService;

    @Transactional
    public List<BillingCycleInvoice> finalizeCycle(BillingCycle cycle) {
        if (cycle == null) {
            throw new IllegalArgumentException("BillingCycle is required");
        }
        if (cycle.getStatus() != BillingStatus.OPEN) {
            throw new IllegalArgumentException("Only OPEN billing cycles can be finalized");
        }
        if (invoiceRepository.existsByBillingCycleId(cycle.getId())) {
            throw new DuplicateEntryException("Invoices already exist for billing cycle " + cycle.getId());
        }

        Apartment apartment = cycle.getApartment();
        List<Household> households = householdRepository.findAllByApartmentId(apartment.getId());
        if (households.isEmpty()) {
            throw new IllegalArgumentException("Cannot finalize: apartment has no households");
        }

        // Calculate total shared purchase cost in the cycle range
        List<BulkWaterPurchase> purchases = bulkWaterPurchaseRepository
                .findAllByApartmentIdAndPurchaseDateBetween(
                        apartment.getId(), cycle.getCycleStartDate(), cycle.getCycleEndDate());

        BigDecimal totalSharedCost = purchases.stream()
                .map(BulkWaterPurchase::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Distribute shared cost
        Map<Household, BigDecimal> sharedAllocations = costDistributionService
                .distributeApartmentCost(apartment, cycle, totalSharedCost);

        List<BillingCycleInvoice> invoices = new ArrayList<>();

        for (Household household : households) {
            // Calculate base charge using TariffCalculationService
            BigDecimal baseCharge = tariffCalculationService.calculateHouseholdBill(household, cycle);

            // Get shared allocation
            BigDecimal sharedAllocation = sharedAllocations.getOrDefault(household, BigDecimal.ZERO);

            BillingCycleInvoice invoice = BillingCycleInvoice.builder()
                    .household(household)
                    .billingCycle(cycle)
                    .baseCharge(baseCharge)
                    .sharedCostAllocation(sharedAllocation)
                    .paidStatus("UNPAID")
                    .build();

            invoice.recomputeTotal();
            invoices.add(invoiceRepository.save(invoice));
        }

        // Update cycle status to finalized
        cycle.setStatus(BillingStatus.FINALIZED);
        billingCycleRepository.save(cycle);

        // Also call alerting: check daily thresholds and detect anomalies
        try {
            alertService.scanForLeaks();
        } catch (Exception e) {
            // Log warning but don't fail finalization if alerting has an issue
            System.err.println("Warning: anomaly scan failed: " + e.getMessage());
        }

        return invoices;
    }
}
