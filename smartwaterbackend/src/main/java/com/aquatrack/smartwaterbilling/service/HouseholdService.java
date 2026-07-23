package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.dto.household.*;
import com.aquatrack.smartwaterbilling.entity.Apartment;
import com.aquatrack.smartwaterbilling.entity.Household;
import com.aquatrack.smartwaterbilling.entity.User;
import com.aquatrack.smartwaterbilling.entity.enums.Role;
import com.aquatrack.smartwaterbilling.exception.DuplicateEntryException;
import com.aquatrack.smartwaterbilling.exception.ResourceNotFoundException;
import com.aquatrack.smartwaterbilling.repository.ApartmentRepository;
import com.aquatrack.smartwaterbilling.repository.HouseholdRepository;
import com.aquatrack.smartwaterbilling.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Business logic for household registration, resident assignment, and meter configuration.
 */
@Service
@RequiredArgsConstructor
public class HouseholdService {

    private final HouseholdRepository householdRepository;
    private final ApartmentRepository apartmentRepository;
    private final UserRepository userRepository;

    // ----------------------------------------------------------------
    // Create household
    // ----------------------------------------------------------------

    @Transactional
    public HouseholdResponse create(HouseholdRequest request) {
        Apartment apartment = apartmentRepository.findById(request.getApartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Apartment", request.getApartmentId()));

        if (householdRepository.existsByApartmentIdAndFlatNumber(
                request.getApartmentId(), request.getFlatNumber())) {
            throw new DuplicateEntryException(
                    "Flat '" + request.getFlatNumber() + "' already exists in this apartment");
        }

        BigDecimal threshold = request.getDailyThresholdLiters() != null
                ? request.getDailyThresholdLiters()
                : BigDecimal.valueOf(500.00);

        Household household = Household.builder()
                .apartment(apartment)
                .flatNumber(request.getFlatNumber())
                .areaSqft(request.getAreaSqft())
                .occupancyCount(request.getOccupancyCount())
                .hasMeter(request.getHasMeter())
                .dailyThresholdLiters(threshold)
                .build();

        return toResponse(householdRepository.save(household));
    }

    // ----------------------------------------------------------------
    // Get household
    // ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public HouseholdResponse findById(Long id) {
        return toResponse(householdRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Household", id)));
    }

    // ----------------------------------------------------------------
    // Assign resident to household
    // ----------------------------------------------------------------

    @Transactional
    public HouseholdResponse assignResident(Long householdId, AssignResidentRequest request) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Household", householdId));

        User resident = userRepository.findByEmail(request.getResidentEmail())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with email: " + request.getResidentEmail()));

        if (resident.getRole() != Role.RESIDENT) {
            throw new IllegalArgumentException(
                    "User '" + request.getResidentEmail() + "' is not a RESIDENT");
        }

        resident.setHousehold(household);
        userRepository.save(resident);

        return toResponse(household);
    }

    // ----------------------------------------------------------------
    // Update meter configuration
    // ----------------------------------------------------------------

    @Transactional
    public HouseholdResponse updateMeterConfig(Long householdId, MeterConfigRequest request) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Household", householdId));

        household.setHasMeter(request.getHasMeter());

        if (request.getDailyThresholdLiters() != null) {
            household.setDailyThresholdLiters(request.getDailyThresholdLiters());
        }

        return toResponse(householdRepository.save(household));
    }

    // ----------------------------------------------------------------
    // Mapper
    // ----------------------------------------------------------------

    public static HouseholdResponse toResponse(Household household) {
        return HouseholdResponse.builder()
                .id(household.getId())
                .apartmentId(household.getApartment().getId())
                .apartmentName(household.getApartment().getName())
                .flatNumber(household.getFlatNumber())
                .areaSqft(household.getAreaSqft())
                .occupancyCount(household.getOccupancyCount())
                .hasMeter(household.getHasMeter())
                .dailyThresholdLiters(household.getDailyThresholdLiters())
                .createdAt(household.getCreatedAt())
                .updatedAt(household.getUpdatedAt())
                .build();
    }
}
