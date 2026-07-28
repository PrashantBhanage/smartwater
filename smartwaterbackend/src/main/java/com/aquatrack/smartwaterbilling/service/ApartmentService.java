package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.dto.apartment.ApartmentRequest;
import com.aquatrack.smartwaterbilling.dto.apartment.ApartmentResponse;
import com.aquatrack.smartwaterbilling.entity.Apartment;
import com.aquatrack.smartwaterbilling.exception.ResourceNotFoundException;
import com.aquatrack.smartwaterbilling.repository.ApartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for apartment onboarding and retrieval.
 */
@Service
@RequiredArgsConstructor
public class ApartmentService {

    private final ApartmentRepository apartmentRepository;

    // ----------------------------------------------------------------
    // Create
    // ----------------------------------------------------------------

    @Transactional
    public ApartmentResponse create(ApartmentRequest request) {
        Apartment apartment = Apartment.builder()
                .name(request.getName())
                .address(request.getAddress())
                .totalHouseholds(request.getTotalHouseholds())
                .adminContact(request.getAdminContact())
                .build();

        return toResponse(apartmentRepository.save(apartment));
    }

    // ----------------------------------------------------------------
    // Read
    // ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public ApartmentResponse findById(Long id) {
        return toResponse(apartmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Apartment", id)));
    }

    @Transactional(readOnly = true)
    public List<ApartmentResponse> findAll() {
        return apartmentRepository.findAll().stream()
                .map(ApartmentService::toResponse)
                .toList();
    }

    // ----------------------------------------------------------------
    // Mapper
    // ----------------------------------------------------------------

    public static ApartmentResponse toResponse(Apartment apartment) {
        return ApartmentResponse.builder()
                .id(apartment.getId())
                .name(apartment.getName())
                .address(apartment.getAddress())
                .totalHouseholds(apartment.getTotalHouseholds())
                .adminContact(apartment.getAdminContact())
                .createdAt(apartment.getCreatedAt())
                .updatedAt(apartment.getUpdatedAt())
                .build();
    }
}
