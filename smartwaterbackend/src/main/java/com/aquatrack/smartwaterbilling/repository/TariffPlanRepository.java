package com.aquatrack.smartwaterbilling.repository;

import com.aquatrack.smartwaterbilling.entity.TariffPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TariffPlanRepository extends JpaRepository<TariffPlan, Long> {

    List<TariffPlan> findAllByApartmentIdOrderByEffectiveFromDateDesc(Long apartmentId);

    /**
     * Finds the tariff plan active on or before a given date
     * (used by Module 2 billing calculation).
     */
    Optional<TariffPlan> findFirstByApartmentIdAndEffectiveFromDateLessThanEqualOrderByEffectiveFromDateDesc(
            Long apartmentId, LocalDate date);
}
