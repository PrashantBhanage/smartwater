package com.aquatrack.smartwaterbilling.controller;

import com.aquatrack.smartwaterbilling.dto.billing.InvoiceResponse;
import com.aquatrack.smartwaterbilling.entity.Invoice;
import com.aquatrack.smartwaterbilling.exception.ResourceNotFoundException;
import com.aquatrack.smartwaterbilling.repository.InvoiceRepository;
import com.aquatrack.smartwaterbilling.service.InvoicePdfService;
import com.aquatrack.smartwaterbilling.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService    invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final InvoicePdfService invoicePdfService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<InvoiceResponse>> listByHousehold(
            @RequestParam Long householdId) {
        return ResponseEntity.ok(invoiceService.listByHousehold(householdId));
    }

    /**
     * Downloads a PDF copy of the invoice identified by {@code id}.
     *
     * <p>Returns {@code 200 OK} with {@code Content-Type: application/pdf} and
     * {@code Content-Disposition: attachment; filename="invoice_{id}.pdf"}.
     *
     * @param id the Invoice primary key (from the {@code invoices} table)
     * @return PDF byte stream
     */
    @GetMapping(value = "/{id}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable Long id) throws IOException {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id));

        byte[] pdfBytes = invoicePdfService.generatePdf(invoice);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "invoice_" + id + ".pdf");
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}
