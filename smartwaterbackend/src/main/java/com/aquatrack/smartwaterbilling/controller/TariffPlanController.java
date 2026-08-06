package com.aquatrack.smartwaterbilling.controller;

import com.aquatrack.smartwaterbilling.dto.tariff.TariffPlanRequest;
import com.aquatrack.smartwaterbilling.dto.tariff.TariffPlanResponse;
import com.aquatrack.smartwaterbilling.service.TariffPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tariff plan management.
 *
 * <ul>
 *   <li>{@code POST /api/tariff-plans} — ADMIN</li>
 *   <li>{@code GET  /api/tariff-plans?apartmentId=} — authenticated</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tariff-plans")
@RequiredArgsConstructor
public class TariffPlanController {

    private final TariffPlanService tariffPlanService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TariffPlanResponse> create(@Valid @RequestBody TariffPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tariffPlanService.create(request));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TariffPlanResponse>> listByApartment(@RequestParam Long apartmentId) {
        return ResponseEntity.ok(tariffPlanService.listByApartment(apartmentId));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TariffPlanResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody com.aquatrack.smartwaterbilling.dto.tariff.TariffPlanUpdateRequest request) {
        return ResponseEntity.ok(tariffPlanService.update(id, request));
    }
}

