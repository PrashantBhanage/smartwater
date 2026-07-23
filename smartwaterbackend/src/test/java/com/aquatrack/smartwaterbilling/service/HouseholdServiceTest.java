package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.dto.household.HouseholdRequest;
import com.aquatrack.smartwaterbilling.dto.household.MeterConfigRequest;
import com.aquatrack.smartwaterbilling.entity.Apartment;
import com.aquatrack.smartwaterbilling.entity.Household;
import com.aquatrack.smartwaterbilling.exception.DuplicateEntryException;
import com.aquatrack.smartwaterbilling.exception.ResourceNotFoundException;
import com.aquatrack.smartwaterbilling.repository.ApartmentRepository;
import com.aquatrack.smartwaterbilling.repository.HouseholdRepository;
import com.aquatrack.smartwaterbilling.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HouseholdService unit tests")
class HouseholdServiceTest {

    @Mock private HouseholdRepository householdRepository;
    @Mock private ApartmentRepository apartmentRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private HouseholdService householdService;

    private Apartment apartment;

    @BeforeEach
    void setUp() {
        apartment = Apartment.builder()
                .id(1L).name("Sunrise Residency")
                .address("42 Park Ave").totalHouseholds(50)
                .adminContact("admin@sunrise.com").build();
    }

    // ----------------------------------------------------------------
    // Create — success with default threshold
    // ----------------------------------------------------------------

    @Test
    @DisplayName("create household — default threshold applied when not provided")
    void create_defaultThreshold() {
        HouseholdRequest req = new HouseholdRequest();
        req.setApartmentId(1L);
        req.setFlatNumber("A-101");
        req.setOccupancyCount(3);
        req.setHasMeter(true);
        // dailyThresholdLiters not set

        when(apartmentRepository.findById(1L)).thenReturn(Optional.of(apartment));
        when(householdRepository.existsByApartmentIdAndFlatNumber(1L, "A-101")).thenReturn(false);
        when(householdRepository.save(any(Household.class))).thenAnswer(inv -> {
            Household h = inv.getArgument(0);
            h.setId(10L);
            return h;
        });

        var response = householdService.create(req);

        assertThat(response.getDailyThresholdLiters())
                .isEqualByComparingTo(BigDecimal.valueOf(500.00));
        assertThat(response.getFlatNumber()).isEqualTo("A-101");
    }

    // ----------------------------------------------------------------
    // Create — duplicate flat number → DuplicateEntryException
    // ----------------------------------------------------------------

    @Test
    @DisplayName("create household — duplicate flat number throws DuplicateEntryException")
    void create_duplicateFlatNumber_throws() {
        HouseholdRequest req = new HouseholdRequest();
        req.setApartmentId(1L);
        req.setFlatNumber("B-202");
        req.setOccupancyCount(2);
        req.setHasMeter(false);

        when(apartmentRepository.findById(1L)).thenReturn(Optional.of(apartment));
        when(householdRepository.existsByApartmentIdAndFlatNumber(1L, "B-202")).thenReturn(true);

        assertThatThrownBy(() -> householdService.create(req))
                .isInstanceOf(DuplicateEntryException.class)
                .hasMessageContaining("B-202");
    }

    // ----------------------------------------------------------------
    // Create — apartment not found → ResourceNotFoundException
    // ----------------------------------------------------------------

    @Test
    @DisplayName("create household — apartment not found throws ResourceNotFoundException")
    void create_apartmentNotFound_throws() {
        HouseholdRequest req = new HouseholdRequest();
        req.setApartmentId(999L);
        req.setFlatNumber("C-303");
        req.setOccupancyCount(1);
        req.setHasMeter(false);

        when(apartmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> householdService.create(req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ----------------------------------------------------------------
    // updateMeterConfig — threshold update only when provided
    // ----------------------------------------------------------------

    @Test
    @DisplayName("updateMeterConfig — updates threshold when provided")
    void updateMeterConfig_updatesThreshold() {
        Household existing = Household.builder()
                .id(5L).apartment(apartment).flatNumber("D-404")
                .occupancyCount(2).hasMeter(false)
                .dailyThresholdLiters(BigDecimal.valueOf(500)).build();

        MeterConfigRequest req = new MeterConfigRequest();
        req.setHasMeter(true);
        req.setDailyThresholdLiters(BigDecimal.valueOf(300));

        when(householdRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(householdRepository.save(any(Household.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = householdService.updateMeterConfig(5L, req);

        assertThat(response.getHasMeter()).isTrue();
        assertThat(response.getDailyThresholdLiters())
                .isEqualByComparingTo(BigDecimal.valueOf(300));
    }

    // ----------------------------------------------------------------
    // updateMeterConfig — threshold unchanged when not provided
    // ----------------------------------------------------------------

    @Test
    @DisplayName("updateMeterConfig — threshold unchanged when not provided")
    void updateMeterConfig_noThresholdChange() {
        Household existing = Household.builder()
                .id(6L).apartment(apartment).flatNumber("E-505")
                .occupancyCount(1).hasMeter(true)
                .dailyThresholdLiters(BigDecimal.valueOf(700)).build();

        MeterConfigRequest req = new MeterConfigRequest();
        req.setHasMeter(false);
        // No threshold change

        when(householdRepository.findById(6L)).thenReturn(Optional.of(existing));
        when(householdRepository.save(any(Household.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = householdService.updateMeterConfig(6L, req);

        assertThat(response.getHasMeter()).isFalse();
        assertThat(response.getDailyThresholdLiters())
                .isEqualByComparingTo(BigDecimal.valueOf(700)); // unchanged
    }
}
