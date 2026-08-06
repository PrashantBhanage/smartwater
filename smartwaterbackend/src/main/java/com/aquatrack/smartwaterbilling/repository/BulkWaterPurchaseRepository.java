package com.aquatrack.smartwaterbilling.repository;

import com.aquatrack.smartwaterbilling.entity.BulkWaterPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BulkWaterPurchaseRepository extends JpaRepository<BulkWaterPurchase, Long> {

    List<BulkWaterPurchase> findAllByApartmentId(Long apartmentId);

    List<BulkWaterPurchase> findAllByApartmentIdAndPurchaseDateBetween(
            Long apartmentId, LocalDate startDate, LocalDate endDate);
}
