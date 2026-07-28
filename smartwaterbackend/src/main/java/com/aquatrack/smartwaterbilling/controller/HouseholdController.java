package com.aquatrack.smartwaterbilling.controller;

import com.aquatrack.smartwaterbilling.dto.household.*;
import com.aquatrack.smartwaterbilling.service.HouseholdService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for household management.
 *
 * <ul>
 *   <li>{@code POST  /api/households}                        — ADMIN only</li>
 *   <li>{@code GET   /api/households/{id}}                   — any authenticated user</li>
 *   <li>{@code POST  /api/households/{id}/assign-resident}   — ADMIN only</li>
 *   <li>{@code PATCH /api/households/{id}/meter-config}      — ADMIN only</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/households")
@RequiredArgsConstructor
public class HouseholdController {

    private final HouseholdService householdService;

    /**
     * Register a new household within an apartment complex.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HouseholdResponse> create(@Valid @RequestBody HouseholdRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(householdService.create(request));
    }

    /**
     * List households for an apartment (used by registration + admin panel).
     */
    @GetMapping
    public ResponseEntity<List<HouseholdResponse>> listByApartment(
            @RequestParam Long apartmentId) {
        return ResponseEntity.ok(householdService.findByApartmentId(apartmentId));
    }

    /**
     * Retrieve household details by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<HouseholdResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(householdService.findById(id));
    }

    /**
     * Assign an existing RESIDENT user to this household.
     */
    @PostMapping("/{id}/assign-resident")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HouseholdResponse> assignResident(
            @PathVariable Long id,
            @Valid @RequestBody AssignResidentRequest request) {
        return ResponseEntity.ok(householdService.assignResident(id, request));
    }

    /**
     * Update meter installation flag and/or daily threshold for a household.
     */
    @PatchMapping("/{id}/meter-config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HouseholdResponse> updateMeterConfig(
            @PathVariable Long id,
            @Valid @RequestBody MeterConfigRequest request) {
        return ResponseEntity.ok(householdService.updateMeterConfig(id, request));
    }
}
