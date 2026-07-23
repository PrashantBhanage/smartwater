package com.aquatrack.smartwaterbilling.repository;

import com.aquatrack.smartwaterbilling.entity.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HouseholdRepository extends JpaRepository<Household, Long> {

    List<Household> findAllByApartmentId(Long apartmentId);

    boolean existsByApartmentIdAndFlatNumber(Long apartmentId, String flatNumber);
}
