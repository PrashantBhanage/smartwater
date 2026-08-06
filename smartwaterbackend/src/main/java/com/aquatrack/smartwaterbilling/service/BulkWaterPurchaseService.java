package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.dto.purchase.BulkPurchaseRequest;
import com.aquatrack.smartwaterbilling.dto.purchase.BulkPurchaseResponse;
import com.aquatrack.smartwaterbilling.dto.purchase.BulkPurchaseSummaryResponse;
import com.aquatrack.smartwaterbilling.entity.Apartment;
import com.aquatrack.smartwaterbilling.entity.BulkWaterPurchase;
import com.aquatrack.smartwaterbilling.exception.ResourceNotFoundException;
import com.aquatrack.smartwaterbilling.repository.ApartmentRepository;
import com.aquatrack.smartwaterbilling.repository.BulkWaterPurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BulkWaterPurchaseService {

    private final BulkWaterPurchaseRepository bulkWaterPurchaseRepository;
    private final ApartmentRepository apartmentRepository;

    @Transactional
    public BulkPurchaseResponse createPurchase(Long apartmentId, BulkPurchaseRequest request) {
        Apartment apartment = apartmentRepository.findById(apartmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Apartment", apartmentId));

        BulkWaterPurchase purchase = BulkWaterPurchase.builder()
                .apartment(apartment)
                .purchaseDate(request.getPurchaseDate())
                .volumeLiters(request.getVolumeLiters())
                .unitCost(request.getUnitCost())
                .build();

        BulkWaterPurchase saved = bulkWaterPurchaseRepository.save(purchase);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BulkPurchaseSummaryResponse getPurchases(Long apartmentId, LocalDate start, LocalDate end) {
        if (!apartmentRepository.existsById(apartmentId)) {
            throw new ResourceNotFoundException("Apartment", apartmentId);
        }

        List<BulkWaterPurchase> purchases;
        if (start != null && end != null) {
            purchases = bulkWaterPurchaseRepository.findAllByApartmentIdAndPurchaseDateBetween(apartmentId, start, end);
        } else {
            purchases = bulkWaterPurchaseRepository.findAllByApartmentId(apartmentId);
        }

        BigDecimal totalVolume = purchases.stream()
                .map(BulkWaterPurchase::getVolumeLiters)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCost = purchases.stream()
                .map(BulkWaterPurchase::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BulkPurchaseResponse> list = purchases.stream()
                .map(this::toResponse)
                .toList();

        return BulkPurchaseSummaryResponse.builder()
                .totalVolumeLiters(totalVolume)
                .totalCost(totalCost.setScale(2, RoundingMode.HALF_UP))
                .purchases(list)
                .build();
    }

    private BulkPurchaseResponse toResponse(BulkWaterPurchase p) {
        return BulkPurchaseResponse.builder()
                .id(p.getId())
                .apartmentId(p.getApartment().getId())
                .purchaseDate(p.getPurchaseDate())
                .volumeLiters(p.getVolumeLiters())
                .unitCost(p.getUnitCost())
                .totalCost(p.totalCost().setScale(2, RoundingMode.HALF_UP))
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
