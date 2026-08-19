package com.aquatrack.smartwaterbilling.integration;

import com.aquatrack.smartwaterbilling.AbstractIT;
import com.aquatrack.smartwaterbilling.TestAuthHelper;
import com.aquatrack.smartwaterbilling.dto.billing.BillingCycleRequest;
import com.aquatrack.smartwaterbilling.dto.purchase.BulkPurchaseRequest;
import com.aquatrack.smartwaterbilling.dto.tariff.TariffPlanRequest;
import com.aquatrack.smartwaterbilling.dto.usage.UsageLogRequest;
import com.aquatrack.smartwaterbilling.entity.enums.UsageSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("End-to-End (E2E) Water Billing & PDF Download Integration Tests")
class E2EWaterBillingWorkflowIT extends AbstractIT {

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String residentToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = TestAuthHelper.obtainAdminToken(mockMvc);
        residentToken = TestAuthHelper.residentToken(mockMvc);
    }

    @Test
    @DisplayName("Verify complete E2E flow: Tariff -> Cycle -> Meter Log -> Bulk Purchase -> Finalize -> Resident Invoices -> PDF Download")
    void e2e_waterBillingAndPdfDownloadWorkflow() throws Exception {
        // 1. Create Active Tariff Plan
        TariffPlanRequest tariffReq = new TariffPlanRequest();
        tariffReq.setApartmentId(SEEDED_APARTMENT_ID);
        tariffReq.setTier1LimitKl(new BigDecimal("15.000"));
        tariffReq.setTier1Rate(new BigDecimal("12.0000"));
        tariffReq.setTier2Rate(new BigDecimal("25.0000"));
        tariffReq.setEffectiveFromDate(LocalDate.of(2024, 1, 1));

        mockMvc.perform(post("/api/tariff-plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tariffReq)))
                .andExpect(status().isCreated());

        // 2. Open Billing Cycle
        BillingCycleRequest cycleReq = new BillingCycleRequest();
        cycleReq.setApartmentId(SEEDED_APARTMENT_ID);
        cycleReq.setCycleStartDate(LocalDate.of(2024, 7, 1));
        cycleReq.setCycleEndDate(LocalDate.of(2024, 7, 31));

        String cycleResp = mockMvc.perform(post("/api/billing-cycles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cycleReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        Long cycleId = objectMapper.readTree(cycleResp).get("id").asLong();

        // 3. Log Meter Readings (Meter Reading & Consumption)
        UsageLogRequest logReq1 = new UsageLogRequest();
        logReq1.setHouseholdId(SEEDED_HOUSEHOLD_ID);
        logReq1.setReadingDate(LocalDate.of(2024, 7, 10));
        logReq1.setVolumeUsedLiters(new BigDecimal("8000.00")); // 8 kL
        logReq1.setSource(UsageSource.MANUAL);

        mockMvc.perform(post("/api/usage-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logReq1)))
                .andExpect(status().isCreated());

        UsageLogRequest logReq2 = new UsageLogRequest();
        logReq2.setHouseholdId(SEEDED_HOUSEHOLD_ID);
        logReq2.setReadingDate(LocalDate.of(2024, 7, 20));
        logReq2.setVolumeUsedLiters(new BigDecimal("10000.00")); // 10 kL (Total = 18 kL)
        logReq2.setSource(UsageSource.MANUAL);

        mockMvc.perform(post("/api/usage-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logReq2)))
                .andExpect(status().isCreated());

        // 4. Record Bulk Water Purchase (Shared Area Cost Allocation)
        BulkPurchaseRequest purchaseReq = new BulkPurchaseRequest();
        purchaseReq.setPurchaseDate(LocalDate.of(2024, 7, 15));
        purchaseReq.setVolumeLiters(new BigDecimal("5000.00"));
        purchaseReq.setUnitCost(new BigDecimal("0.4000")); // Total shared cost = 2000.00

        mockMvc.perform(post("/api/apartments/" + SEEDED_APARTMENT_ID + "/bulk-purchases")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(purchaseReq)))
                .andExpect(status().isCreated());

        // 5. Finalize Billing Cycle
        mockMvc.perform(patch("/api/billing-cycles/" + cycleId + "/finalize")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.invoicesGenerated").value(greaterThanOrEqualTo(1)));

        // 6. Test Fetching Resident Invoices (GET /api/invoices/resident and GET /api/invoices)
        String residentInvoicesResp = mockMvc.perform(get("/api/invoices/resident")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].id", notNullValue()))
                .andExpect(jsonPath("$[0].status").value("ISSUED"))
                .andReturn().getResponse().getContentAsString();

        JsonNode invoicesNode = objectMapper.readTree(residentInvoicesResp);
        Long invoiceId = invoicesNode.get(0).get("id").asLong();

        // Also test GET /api/invoices?householdId={id}
        mockMvc.perform(get("/api/invoices")
                        .header("Authorization", "Bearer " + residentToken)
                        .param("householdId", String.valueOf(SEEDED_HOUSEHOLD_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(invoiceId));

        // 7. PDF Generation & Endpoint Test (GET /api/invoices/{id}/download)
        byte[] pdfBytes = mockMvc.perform(get("/api/invoices/" + invoiceId + "/download")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", containsString("invoice_" + invoiceId + ".pdf")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(pdfBytes).isNotNull().isNotEmpty();
        // Assert PDF magic header %PDF
        String pdfHeader = new String(pdfBytes, 0, 4);
        assertThat(pdfHeader).isEqualTo("%PDF");
    }
}
