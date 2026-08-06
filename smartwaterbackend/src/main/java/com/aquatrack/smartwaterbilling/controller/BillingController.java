package com.aquatrack.smartwaterbilling.controller;

import com.aquatrack.smartwaterbilling.dto.billing.BillingCycleInvoiceResponse;
import com.aquatrack.smartwaterbilling.dto.billing.InvoiceSummaryResponse;
import com.aquatrack.smartwaterbilling.entity.BillingCycle;
import com.aquatrack.smartwaterbilling.entity.BillingCycleInvoice;
import com.aquatrack.smartwaterbilling.entity.User;
import com.aquatrack.smartwaterbilling.entity.enums.Role;
import com.aquatrack.smartwaterbilling.exception.ResourceNotFoundException;
import com.aquatrack.smartwaterbilling.repository.BillingCycleRepository;
import com.aquatrack.smartwaterbilling.repository.BillingCycleInvoiceRepository;
import com.aquatrack.smartwaterbilling.service.InvoiceGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BillingController {

    private final InvoiceGenerationService invoiceGenerationService;
    private final BillingCycleRepository billingCycleRepository;
    private final BillingCycleInvoiceRepository invoiceRepository;

    @PostMapping("/billing-cycles/{cycleId}/finalize")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvoiceSummaryResponse> finalizeCycle(@PathVariable Long cycleId) {
        BillingCycle cycle = billingCycleRepository.findById(cycleId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingCycle", cycleId));

        List<BillingCycleInvoice> invoices = invoiceGenerationService.finalizeCycle(cycle);

        BigDecimal totalBase = invoices.stream()
                .map(BillingCycleInvoice::getBaseCharge)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalShared = invoices.stream()
                .map(BillingCycleInvoice::getSharedCostAllocation)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAmount = invoices.stream()
                .map(BillingCycleInvoice::getTotalCharge)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        InvoiceSummaryResponse summary = InvoiceSummaryResponse.builder()
                .invoicesGenerated(invoices.size())
                .totalBaseCharge(totalBase)
                .totalSharedAllocation(totalShared)
                .totalAmount(totalAmount)
                .build();

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/billing-cycles/{cycleId}/invoices")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public ResponseEntity<List<BillingCycleInvoiceResponse>> getInvoices(
            @PathVariable Long cycleId,
            @AuthenticationPrincipal User currentUser) {

        BillingCycle cycle = billingCycleRepository.findById(cycleId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingCycle", cycleId));

        List<BillingCycleInvoice> invoices;
        if (currentUser.getRole() == Role.ADMIN) {
            invoices = invoiceRepository.findAllByBillingCycleId(cycleId);
        } else {
            // Resident sees only theirs
            if (currentUser.getHousehold() == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }
            invoices = invoiceRepository.findByHouseholdIdAndBillingCycleId(
                    currentUser.getHousehold().getId(), cycleId)
                    .map(List::of)
                    .orElse(Collections.emptyList());
        }

        List<BillingCycleInvoiceResponse> responses = invoices.stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/residents/my-invoices")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public ResponseEntity<List<BillingCycleInvoiceResponse>> getMyInvoices(
            @AuthenticationPrincipal User currentUser) {

        if (currentUser.getHousehold() == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<BillingCycleInvoice> invoices = invoiceRepository.findAllByHouseholdId(
                currentUser.getHousehold().getId());

        List<BillingCycleInvoiceResponse> responses = invoices.stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    private BillingCycleInvoiceResponse toResponse(BillingCycleInvoice invoice) {
        return BillingCycleInvoiceResponse.builder()
                .id(invoice.getId())
                .householdId(invoice.getHousehold().getId())
                .flatNumber(invoice.getHousehold().getFlatNumber())
                .billingCycleId(invoice.getBillingCycle().getId())
                .baseCharge(invoice.getBaseCharge())
                .sharedCostAllocation(invoice.getSharedCostAllocation())
                .totalCharge(invoice.getTotalCharge())
                .paidStatus(invoice.getPaidStatus())
                .createdAt(invoice.getCreatedAt())
                .build();
    }
}
