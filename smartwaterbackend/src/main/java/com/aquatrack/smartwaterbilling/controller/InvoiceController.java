package com.aquatrack.smartwaterbilling.controller;

import com.aquatrack.smartwaterbilling.dto.billing.InvoiceResponse;
import com.aquatrack.smartwaterbilling.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<InvoiceResponse>> listByHousehold(
            @RequestParam Long householdId) {
        return ResponseEntity.ok(invoiceService.listByHousehold(householdId));
    }
}
