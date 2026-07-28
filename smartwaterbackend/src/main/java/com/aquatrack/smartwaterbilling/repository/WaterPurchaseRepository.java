package com.aquatrack.smartwaterbilling.repository;

import com.aquatrack.smartwaterbilling.entity.WaterPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WaterPurchaseRepository extends JpaRepository<WaterPurchase, Long> {

    List<WaterPurchase> findAllByBillingCycleId(Long cycleId);

    List<WaterPurchase> findAllByApartmentId(Long apartmentId);
}
