package com.aquatrack.smartwaterbilling.repository;

import com.aquatrack.smartwaterbilling.entity.BillingCycle;
import com.aquatrack.smartwaterbilling.entity.enums.BillingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillingCycleRepository extends JpaRepository<BillingCycle, Long> {

    List<BillingCycle> findAllByApartmentId(Long apartmentId);

    Optional<BillingCycle> findByApartmentIdAndStatus(Long apartmentId, BillingStatus status);
}
