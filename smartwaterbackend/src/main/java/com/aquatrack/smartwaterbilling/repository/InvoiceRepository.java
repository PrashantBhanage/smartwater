package com.aquatrack.smartwaterbilling.repository;

import com.aquatrack.smartwaterbilling.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findAllByBillingCycleId(Long cycleId);

    List<Invoice> findAllByHouseholdId(Long householdId);

    Optional<Invoice> findByHouseholdIdAndBillingCycleId(Long householdId, Long cycleId);

    boolean existsByBillingCycleId(Long cycleId);
}
