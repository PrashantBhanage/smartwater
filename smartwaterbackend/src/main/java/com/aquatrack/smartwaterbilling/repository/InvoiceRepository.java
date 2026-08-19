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

    @org.springframework.data.jpa.repository.Query("SELECT i FROM Invoice i JOIN FETCH i.household h JOIN FETCH h.apartment a JOIN FETCH i.billingCycle c WHERE i.id = :id")
    Optional<Invoice> findByIdWithDetails(@org.springframework.data.repository.query.Param("id") Long id);

    boolean existsByBillingCycleId(Long cycleId);
}
