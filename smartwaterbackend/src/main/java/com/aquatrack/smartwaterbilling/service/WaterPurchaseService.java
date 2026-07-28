package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.dto.purchase.PurchaseRequest;
import com.aquatrack.smartwaterbilling.dto.purchase.PurchaseResponse;
import com.aquatrack.smartwaterbilling.entity.Apartment;
import com.aquatrack.smartwaterbilling.entity.BillingCycle;
import com.aquatrack.smartwaterbilling.entity.WaterPurchase;
import com.aquatrack.smartwaterbilling.entity.enums.BillingStatus;
import com.aquatrack.smartwaterbilling.exception.ResourceNotFoundException;
import com.aquatrack.smartwaterbilling.repository.ApartmentRepository;
import com.aquatrack.smartwaterbilling.repository.BillingCycleRepository;
import com.aquatrack.smartwaterbilling.repository.WaterPurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Records apartment-level bulk water purchases against an OPEN billing cycle.
 */
@Service
@RequiredArgsConstructor
public class WaterPurchaseService {

    private final WaterPurchaseRepository purchaseRepository;
    private final ApartmentRepository apartmentRepository;
    private final BillingCycleRepository billingCycleRepository;

    @Transactional
    public PurchaseResponse recordPurchase(PurchaseRequest request) {
        Apartment apartment = apartmentRepository.findById(request.getApartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Apartment", request.getApartmentId()));

        BillingCycle cycle = billingCycleRepository.findById(request.getCycleId())
                .orElseThrow(() -> new ResourceNotFoundException("BillingCycle", request.getCycleId()));

        if (!cycle.getApartment().getId().equals(apartment.getId())) {
            throw new IllegalArgumentException(
                    "Billing cycle " + cycle.getId() + " does not belong to apartment " + apartment.getId());
        }
        if (cycle.getStatus() != BillingStatus.OPEN) {
            throw new IllegalArgumentException(
                    "Purchases can only be recorded against an OPEN billing cycle (current status: "
                            + cycle.getStatus() + ")");
        }

        WaterPurchase purchase = WaterPurchase.builder()
                .apartment(apartment)
                .billingCycle(cycle)
                .volumePurchasedKl(request.getVolumePurchasedKl())
                .unitCost(request.getUnitCost())
                .purchaseDate(request.getPurchaseDate())
                .source(request.getSource())
                .build();

        return toResponse(purchaseRepository.save(purchase));
    }

    @Transactional(readOnly = true)
    public List<PurchaseResponse> listByCycle(Long cycleId) {
        if (!billingCycleRepository.existsById(cycleId)) {
            throw new ResourceNotFoundException("BillingCycle", cycleId);
        }
        return purchaseRepository.findAllByBillingCycleId(cycleId).stream()
                .map(WaterPurchaseService::toResponse)
                .toList();
    }

    public static PurchaseResponse toResponse(WaterPurchase p) {
        return PurchaseResponse.builder()
                .id(p.getId())
                .apartmentId(p.getApartment().getId())
                .cycleId(p.getBillingCycle().getId())
                .volumePurchasedKl(p.getVolumePurchasedKl())
                .unitCost(p.getUnitCost())
                .totalCost(p.totalCost())
                .purchaseDate(p.getPurchaseDate())
                .source(p.getSource())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
