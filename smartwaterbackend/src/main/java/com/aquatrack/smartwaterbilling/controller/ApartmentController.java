package com.aquatrack.smartwaterbilling.controller;

import com.aquatrack.smartwaterbilling.dto.apartment.ApartmentRequest;
import com.aquatrack.smartwaterbilling.dto.apartment.ApartmentResponse;
import com.aquatrack.smartwaterbilling.service.ApartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for apartment onboarding and retrieval.
 *
 * <ul>
 *   <li>{@code POST /api/apartments}   — ADMIN only</li>
 *   <li>{@code GET  /api/apartments/{id}} — any authenticated user</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/apartments")
@RequiredArgsConstructor
public class ApartmentController {

    private final ApartmentService apartmentService;

    /**
     * Onboard a new apartment complex.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApartmentResponse> create(@Valid @RequestBody ApartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(apartmentService.create(request));
    }

    /**
     * Retrieve apartment details by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApartmentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(apartmentService.findById(id));
    }
}
