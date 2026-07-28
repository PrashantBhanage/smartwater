package com.aquatrack.smartwaterbilling.repository;

import com.aquatrack.smartwaterbilling.entity.Alert;
import com.aquatrack.smartwaterbilling.entity.enums.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findAllByHouseholdIdOrderByCreatedAtDesc(Long householdId);

    boolean existsByHouseholdIdAndAlertTypeAndReadingDate(
            Long householdId, AlertType alertType, LocalDate readingDate);
}
