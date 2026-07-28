package com.aquatrack.smartwaterbilling.repository;

import com.aquatrack.smartwaterbilling.entity.WaterUsageLog;
import com.aquatrack.smartwaterbilling.entity.enums.UsageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface WaterUsageLogRepository extends JpaRepository<WaterUsageLog, Long> {

    /**
     * Duplicate detection: returns true if a log already exists for the
     * given household on the given date. Backed by the DB UNIQUE constraint
     * {@code uq_usage_log_household_date}.
     */
    boolean existsByHouseholdIdAndReadingDate(Long householdId, LocalDate readingDate);

    List<WaterUsageLog> findAllByHouseholdId(Long householdId);

    List<WaterUsageLog> findAllByHouseholdIdAndReadingDate(Long householdId, LocalDate readingDate);

    @Query("SELECT w FROM WaterUsageLog w WHERE w.household.apartment.id = :apartmentId " +
           "AND w.readingDate BETWEEN :start AND :end")
    List<WaterUsageLog> findByApartmentAndDateRange(
            @Param("apartmentId") Long apartmentId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("SELECT w.household.id, COALESCE(SUM(w.volumeUsedLiters), 0) " +
           "FROM WaterUsageLog w " +
           "WHERE w.household.apartment.id = :apartmentId " +
           "AND w.readingDate BETWEEN :start AND :end " +
           "GROUP BY w.household.id")
    List<Object[]> sumVolumeByHouseholdInRange(
            @Param("apartmentId") Long apartmentId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    List<WaterUsageLog> findAllByHouseholdIdAndReadingDateBetween(
            Long householdId, LocalDate start, LocalDate end);

    long countByHouseholdIdAndUsageStatus(Long householdId, UsageStatus status);
}
