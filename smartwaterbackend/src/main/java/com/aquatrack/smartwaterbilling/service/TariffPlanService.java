package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.dto.tariff.TariffPlanRequest;
import com.aquatrack.smartwaterbilling.dto.tariff.TariffPlanResponse;
import com.aquatrack.smartwaterbilling.dto.tariff.TariffPlanUpdateRequest;

import com.aquatrack.smartwaterbilling.entity.Apartment;
import com.aquatrack.smartwaterbilling.entity.TariffPlan;
import com.aquatrack.smartwaterbilling.exception.ResourceNotFoundException;
import com.aquatrack.smartwaterbilling.repository.ApartmentRepository;
import com.aquatrack.smartwaterbilling.repository.TariffPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TariffPlanService {

    private final TariffPlanRepository tariffPlanRepository;
    private final ApartmentRepository apartmentRepository;

    @Transactional
    public TariffPlanResponse create(TariffPlanRequest request) {
        Apartment apartment = apartmentRepository.findById(request.getApartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Apartment", request.getApartmentId()));

        TariffPlan plan = TariffPlan.builder()
                .apartment(apartment)
                .tier1LimitKl(request.getTier1LimitKl())
                .tier1Rate(request.getTier1Rate())
                .tier2Rate(request.getTier2Rate())
                .effectiveFromDate(request.getEffectiveFromDate())
                .build();

        return toResponse(tariffPlanRepository.save(plan));
    }

    @Transactional
    public TariffPlanResponse update(Long id, TariffPlanUpdateRequest request) {
        TariffPlan plan = tariffPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TariffPlan", id));

        if (request.getTier1LimitKl() != null) {
            plan.setTier1LimitKl(request.getTier1LimitKl());
        }
        if (request.getTier1Rate() != null) {
            plan.setTier1Rate(request.getTier1Rate());
        }
        if (request.getTier2Rate() != null) {
            plan.setTier2Rate(request.getTier2Rate());
        }
        if (request.getEffectiveFromDate() != null) {
            plan.setEffectiveFromDate(request.getEffectiveFromDate());
        }

        return toResponse(tariffPlanRepository.save(plan));
    }



    @Transactional(readOnly = true)
    public List<TariffPlanResponse> listByApartment(Long apartmentId) {
        if (!apartmentRepository.existsById(apartmentId)) {
            throw new ResourceNotFoundException("Apartment", apartmentId);
        }
        return tariffPlanRepository.findAllByApartmentIdOrderByEffectiveFromDateDesc(apartmentId)
                .stream().map(TariffPlanService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TariffPlan requireActivePlan(Long apartmentId, LocalDate onDate) {
        return tariffPlanRepository
                .findFirstByApartmentIdAndEffectiveFromDateLessThanEqualOrderByEffectiveFromDateDesc(
                        apartmentId, onDate)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No tariff plan effective on " + onDate + " for apartment " + apartmentId));
    }

    public static TariffPlanResponse toResponse(TariffPlan plan) {
        return TariffPlanResponse.builder()
                .id(plan.getId())
                .apartmentId(plan.getApartment().getId())
                .tier1LimitKl(plan.getTier1LimitKl())
                .tier1Rate(plan.getTier1Rate())
                .tier2Rate(plan.getTier2Rate())
                .effectiveFromDate(plan.getEffectiveFromDate())
                .createdAt(plan.getCreatedAt())
                .build();
    }
}
