package com.aquatrack.smartwaterbilling.controller;

import com.aquatrack.smartwaterbilling.dto.purchase.PurchaseRequest;
import com.aquatrack.smartwaterbilling.dto.purchase.PurchaseResponse;
import com.aquatrack.smartwaterbilling.service.WaterPurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for apartment-level water purchase tracking.
 *
 * <ul>
 *   <li>{@code POST /api/purchases} — ADMIN only</li>
 *   <li>{@code GET  /api/purchases?cycleId=} — ADMIN only</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final WaterPurchaseService purchaseService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PurchaseResponse> record(@Valid @RequestBody PurchaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseService.recordPurchase(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PurchaseResponse>> listByCycle(@RequestParam Long cycleId) {
        return ResponseEntity.ok(purchaseService.listByCycle(cycleId));
    }
}
