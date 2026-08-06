package com.aquatrack.smartwaterbilling.repository;

import com.aquatrack.smartwaterbilling.entity.Alert;
import com.aquatrack.smartwaterbilling.entity.enums.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findAllByHouseholdIdOrderByCreatedAtDesc(Long householdId);

    boolean existsByHouseholdIdAndAlertTypeAndReadingDate(
            Long householdId, AlertType alertType, LocalDate readingDate);

    @Query("SELECT a FROM Alert a WHERE a.household.apartment.id = :apartmentId " +
           "AND a.createdAt >= :sinceDate ORDER BY a.createdAt DESC")
    List<Alert> findAllByApartmentIdRecent(
            @Param("apartmentId") Long apartmentId,
            @Param("sinceDate") LocalDateTime sinceDate);
}
