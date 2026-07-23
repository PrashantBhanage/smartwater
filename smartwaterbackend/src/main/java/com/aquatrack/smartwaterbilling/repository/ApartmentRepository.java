package com.aquatrack.smartwaterbilling.repository;

import com.aquatrack.smartwaterbilling.entity.Apartment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApartmentRepository extends JpaRepository<Apartment, Long> {
    boolean existsByName(String name);
}
