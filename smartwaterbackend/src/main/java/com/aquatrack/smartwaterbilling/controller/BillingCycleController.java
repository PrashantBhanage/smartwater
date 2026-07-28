package com.aquatrack.smartwaterbilling.controller;

import com.aquatrack.smartwaterbilling.dto.billing.BillingCycleRequest;
import com.aquatrack.smartwaterbilling.dto.billing.BillingCycleResponse;
import com.aquatrack.smartwaterbilling.dto.billing.InvoiceResponse;
import com.aquatrack.smartwaterbilling.service.BillingCycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Billing cycle lifecycle endpoints.
 *
 * <ul>
 *   <li>{@code POST  /api/billing-cycles} — open a new OPEN cycle (ADMIN)</li>
 *   <li>{@code PATCH /api/billing-cycles/{id}/finalize} — generate invoices (ADMIN)</li>
 *   <li>{@code PATCH /api/billing-cycles/{id}/archive} — archive finalized cycle (ADMIN)</li>
 *   <li>{@code GET   /api/billing-cycles/{id}} — any authenticated user</li>
 *   <li>{@code GET   /api/billing-cycles/{id}/invoices} — any authenticated user</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/billing-cycles")
@RequiredArgsConstructor
public class BillingCycleController {

    private final BillingCycleService billingCycleService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BillingCycleResponse> open(@Valid @RequestBody BillingCycleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(billingCycleService.open(request));
    }

    @PatchMapping("/{id}/finalize")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BillingCycleResponse> finalizeCycle(@PathVariable Long id) {
        return ResponseEntity.ok(billingCycleService.finalizeCycle(id));
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BillingCycleResponse> archive(@PathVariable Long id) {
        return ResponseEntity.ok(billingCycleService.archive(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BillingCycleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(billingCycleService.getById(id));
    }

    @GetMapping("/{id}/invoices")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<InvoiceResponse>> listInvoices(@PathVariable Long id) {
        return ResponseEntity.ok(billingCycleService.listInvoices(id));
    }
}
