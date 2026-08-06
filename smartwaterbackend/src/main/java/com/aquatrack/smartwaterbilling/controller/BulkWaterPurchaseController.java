package com.aquatrack.smartwaterbilling.controller;

import com.aquatrack.smartwaterbilling.dto.purchase.BulkPurchaseRequest;
import com.aquatrack.smartwaterbilling.dto.purchase.BulkPurchaseResponse;
import com.aquatrack.smartwaterbilling.dto.purchase.BulkPurchaseSummaryResponse;
import com.aquatrack.smartwaterbilling.service.BulkWaterPurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/apartments/{apartmentId}/bulk-purchases")
@RequiredArgsConstructor
public class BulkWaterPurchaseController {

    private final BulkWaterPurchaseService bulkWaterPurchaseService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkPurchaseResponse> createPurchase(
            @PathVariable Long apartmentId,
            @Valid @RequestBody BulkPurchaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bulkWaterPurchaseService.createPurchase(apartmentId, request));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BulkPurchaseSummaryResponse> getPurchases(
            @PathVariable Long apartmentId,
            @RequestParam(name = "cycleStart", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate cycleStart,
            @RequestParam(name = "cycleEnd", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate cycleEnd) {
        if ((cycleStart == null) != (cycleEnd == null)) {
            throw new IllegalArgumentException(
                    "Both cycleStart and cycleEnd must be provided together, or neither");
        }
        return ResponseEntity.ok(bulkWaterPurchaseService.getPurchases(apartmentId, cycleStart, cycleEnd));
    }
}
