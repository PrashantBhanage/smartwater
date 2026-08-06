package com.aquatrack.smartwaterbilling.repository;

import com.aquatrack.smartwaterbilling.entity.BillingCycleInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillingCycleInvoiceRepository extends JpaRepository<BillingCycleInvoice, Long> {

    List<BillingCycleInvoice> findAllByBillingCycleId(Long cycleId);

    List<BillingCycleInvoice> findAllByHouseholdId(Long householdId);

    Optional<BillingCycleInvoice> findByHouseholdIdAndBillingCycleId(Long householdId, Long cycleId);

    boolean existsByBillingCycleId(Long cycleId);
}
