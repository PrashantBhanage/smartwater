package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.dto.billing.InvoiceResponse;
import com.aquatrack.smartwaterbilling.exception.ResourceNotFoundException;
import com.aquatrack.smartwaterbilling.repository.HouseholdRepository;
import com.aquatrack.smartwaterbilling.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final HouseholdRepository householdRepository;

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listByHousehold(Long householdId) {
        if (!householdRepository.existsById(householdId)) {
            throw new ResourceNotFoundException("Household", householdId);
        }
        return invoiceRepository.findAllByHouseholdId(householdId).stream()
                .map(BillingCycleService::toInvoiceResponse)
                .toList();
    }
}
