package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.dto.billing.BillingCycleRequest;
import com.aquatrack.smartwaterbilling.dto.billing.BillingCycleResponse;
import com.aquatrack.smartwaterbilling.dto.billing.InvoiceResponse;
import com.aquatrack.smartwaterbilling.entity.*;
import com.aquatrack.smartwaterbilling.entity.enums.BillingStatus;
import com.aquatrack.smartwaterbilling.entity.enums.InvoiceStatus;
import com.aquatrack.smartwaterbilling.exception.DuplicateEntryException;
import com.aquatrack.smartwaterbilling.exception.ResourceNotFoundException;
import com.aquatrack.smartwaterbilling.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Opens, finalizes, and archives billing cycles.
 * On finalize: computes tariff base charges + shared-cost allocations and
 * persists one {@link Invoice} per household.
 */
@Service
@RequiredArgsConstructor
public class BillingCycleService {

    private final BillingCycleRepository billingCycleRepository;
    private final ApartmentRepository apartmentRepository;
    private final HouseholdRepository householdRepository;
    private final WaterUsageLogRepository usageLogRepository;
    private final WaterPurchaseRepository purchaseRepository;
    private final InvoiceRepository invoiceRepository;
    private final TariffPlanService tariffPlanService;
    private final TariffBillingEngine tariffBillingEngine;
    private final CostDistributionService costDistributionService;

    // ----------------------------------------------------------------
    // Open
    // ----------------------------------------------------------------

    @Transactional
    public BillingCycleResponse open(BillingCycleRequest request) {
        if (!request.getCycleEndDate().isAfter(request.getCycleStartDate())) {
            throw new IllegalArgumentException("cycleEndDate must be after cycleStartDate");
        }

        Apartment apartment = apartmentRepository.findById(request.getApartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Apartment", request.getApartmentId()));

        billingCycleRepository.findByApartmentIdAndStatus(apartment.getId(), BillingStatus.OPEN)
                .ifPresent(existing -> {
                    throw new DuplicateEntryException(
                            "Apartment already has an OPEN billing cycle (id=" + existing.getId() + ")");
                });

        BillingCycle cycle = BillingCycle.builder()
                .apartment(apartment)
                .cycleStartDate(request.getCycleStartDate())
                .cycleEndDate(request.getCycleEndDate())
                .status(BillingStatus.OPEN)
                .build();

        return toResponse(billingCycleRepository.save(cycle), null);
    }

    // ----------------------------------------------------------------
    // Finalize
    // ----------------------------------------------------------------

    @Transactional
    public BillingCycleResponse finalizeCycle(Long cycleId) {
        BillingCycle cycle = billingCycleRepository.findById(cycleId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingCycle", cycleId));

        if (cycle.getStatus() != BillingStatus.OPEN) {
            throw new IllegalArgumentException(
                    "Only OPEN cycles can be finalized (current status: " + cycle.getStatus() + ")");
        }
        if (invoiceRepository.existsByBillingCycleId(cycleId)) {
            throw new DuplicateEntryException("Invoices already exist for billing cycle " + cycleId);
        }

        Long apartmentId = cycle.getApartment().getId();
        List<Household> households = householdRepository.findAllByApartmentId(apartmentId);
        if (households.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot finalize: apartment " + apartmentId + " has no households");
        }

        TariffPlan plan = tariffPlanService.requireActivePlan(apartmentId, cycle.getCycleEndDate());

        Map<Long, BigDecimal> usageByHousehold = loadUsageMap(apartmentId, cycle);
        BigDecimal sharedCost = purchaseRepository.findAllByBillingCycleId(cycleId).stream()
                .map(WaterPurchase::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, BigDecimal> allocations = costDistributionService.distribute(
                households, usageByHousehold, sharedCost);

        int generated = 0;
        for (Household household : households) {
            BigDecimal volume = usageByHousehold.getOrDefault(household.getId(), BigDecimal.ZERO);
            BigDecimal baseCharge = Boolean.TRUE.equals(household.getHasMeter())
                    ? tariffBillingEngine.calculateCharge(volume, plan)
                    : BigDecimal.ZERO.setScale(2);

            BigDecimal shared = allocations.getOrDefault(household.getId(), BigDecimal.ZERO);

            Invoice invoice = Invoice.builder()
                    .household(household)
                    .billingCycle(cycle)
                    .baseCharge(baseCharge)
                    .sharedAllocation(shared)
                    .adjustments(BigDecimal.ZERO.setScale(2))
                    .status(InvoiceStatus.ISSUED)
                    .build();
            invoice.recomputeTotal();
            invoiceRepository.save(invoice);
            generated++;
        }

        cycle.setStatus(BillingStatus.FINALIZED);
        billingCycleRepository.save(cycle);

        return toResponse(cycle, generated);
    }

    // ----------------------------------------------------------------
    // Archive
    // ----------------------------------------------------------------

    @Transactional
    public BillingCycleResponse archive(Long cycleId) {
        BillingCycle cycle = billingCycleRepository.findById(cycleId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingCycle", cycleId));

        if (cycle.getStatus() != BillingStatus.FINALIZED) {
            throw new IllegalArgumentException(
                    "Only FINALIZED cycles can be archived (current status: " + cycle.getStatus() + ")");
        }

        cycle.setStatus(BillingStatus.ARCHIVED);
        return toResponse(billingCycleRepository.save(cycle), null);
    }

    // ----------------------------------------------------------------
    // Reads
    // ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public BillingCycleResponse getById(Long cycleId) {
        return toResponse(billingCycleRepository.findById(cycleId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingCycle", cycleId)), null);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listInvoices(Long cycleId) {
        if (!billingCycleRepository.existsById(cycleId)) {
            throw new ResourceNotFoundException("BillingCycle", cycleId);
        }
        return invoiceRepository.findAllByBillingCycleId(cycleId).stream()
                .map(BillingCycleService::toInvoiceResponse)
                .toList();
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private Map<Long, BigDecimal> loadUsageMap(Long apartmentId, BillingCycle cycle) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Object[] row : usageLogRepository.sumVolumeByHouseholdInRange(
                apartmentId, cycle.getCycleStartDate(), cycle.getCycleEndDate())) {
            Long householdId = (Long) row[0];
            BigDecimal volume = (BigDecimal) row[1];
            map.put(householdId, volume != null ? volume : BigDecimal.ZERO);
        }
        return map;
    }

    public static BillingCycleResponse toResponse(BillingCycle cycle, Integer invoicesGenerated) {
        return BillingCycleResponse.builder()
                .id(cycle.getId())
                .apartmentId(cycle.getApartment().getId())
                .cycleStartDate(cycle.getCycleStartDate())
                .cycleEndDate(cycle.getCycleEndDate())
                .status(cycle.getStatus())
                .createdAt(cycle.getCreatedAt())
                .updatedAt(cycle.getUpdatedAt())
                .invoicesGenerated(invoicesGenerated)
                .build();
    }

    public static InvoiceResponse toInvoiceResponse(Invoice invoice) {
        return InvoiceResponse.builder()
                .id(invoice.getId())
                .householdId(invoice.getHousehold().getId())
                .flatNumber(invoice.getHousehold().getFlatNumber())
                .cycleId(invoice.getBillingCycle().getId())
                .baseCharge(invoice.getBaseCharge())
                .sharedAllocation(invoice.getSharedAllocation())
                .adjustments(invoice.getAdjustments())
                .totalAmount(invoice.getTotalAmount())
                .status(invoice.getStatus())
                .createdAt(invoice.getCreatedAt())
                .build();
    }
}
