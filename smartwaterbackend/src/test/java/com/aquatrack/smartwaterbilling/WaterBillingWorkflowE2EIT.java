package com.aquatrack.smartwaterbilling;

import com.aquatrack.smartwaterbilling.dto.auth.LoginRequest;
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

/**
 * End-to-end integration suite for the complete water billing workflow.
 *
 * <p>Each test method rebuilds the workflow from scratch (tariff plan →
 * billing cycle → meter readings → bulk purchase → finalize) on the fresh
 * seeded database provided by {@link AbstractIT}, then exercises the three
 * guarantees required for stability:
 *
 * <ol>
 *   <li>JWT login / authentication</li>
 *   <li>Resident invoice listing ({@code GET /api/invoices/resident})</li>
 *   <li>PDF download ({@code GET /api/invoices/{id}/download}) returning a
 *       valid, non-empty {@code application/pdf} byte stream</li>
 * </ol>
 */
@DisplayName("E2E Water Billing Workflow — JWT Auth, Resident Invoices & PDF Download")
class WaterBillingWorkflowE2EIT extends AbstractIT {

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String residentToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = TestAuthHelper.obtainAdminToken(mockMvc);
        residentToken = TestAuthHelper.residentToken(mockMvc);
    }

    // ----------------------------------------------------------------
    // 1. Login / Authentication using JWT
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Resident login returns 200 with a non-empty JWT and RESIDENT role")
    void login_withValidResidentCredentials_returnsJwt() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                loginRequest(SEEDED_RESIDENT_EMAIL, SEEDED_RESIDENT_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("RESIDENT"))
                .andExpect(jsonPath("$.email").value(SEEDED_RESIDENT_EMAIL));
    }

    @Test
    @DisplayName("Resident invoice endpoint rejects unauthenticated requests with 401")
    void residentInvoices_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/invoices/resident"))
                .andExpect(status().is(anyOf(equalTo(401), equalTo(403))));
    }

    // ----------------------------------------------------------------
    // 2. Resident invoice listing (GET /api/invoices/resident)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Resident can fetch finalized, ISSUED invoices from /api/invoices/resident")
    void residentFetchesInvoices_returnsIssuedInvoices() throws Exception {
        runFullBillingWorkflow();

        mockMvc.perform(get("/api/invoices/resident")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].id", notNullValue()))
                .andExpect(jsonPath("$[0].status").value("ISSUED"))
                .andExpect(jsonPath("$[0].totalAmount", notNullValue()));
    }

    @Test
    @DisplayName("Resident can fetch own invoices with explicit householdId filter")
    void residentFetchesInvoices_withHouseholdId_returnsOwnInvoices() throws Exception {
        runFullBillingWorkflow();

        mockMvc.perform(get("/api/invoices/resident")
                        .header("Authorization", "Bearer " + residentToken)
                        .param("householdId", String.valueOf(SEEDED_HOUSEHOLD_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].id", not(empty())))
                .andExpect(jsonPath("$[*].status", everyItem(equalTo("ISSUED"))));
    }

    // ----------------------------------------------------------------
    // 3. PDF download (GET /api/invoices/{id}/download)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("PDF download returns 200, application/pdf content type and non-empty byte stream")
    void pdfDownload_returns200_withApplicationPdfAndNonEmptyBytes() throws Exception {
        Long invoiceId = runFullBillingWorkflow();

        byte[] pdfBytes = mockMvc.perform(get("/api/invoices/" + invoiceId + "/download")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", containsString("invoice_" + invoiceId + ".pdf")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(pdfBytes).isNotNull().isNotEmpty();
        assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("PDF download for a non-existent invoice returns 404")
    void pdfDownload_unknownInvoice_returns404() throws Exception {
        mockMvc.perform(get("/api/invoices/999999/download")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------
    // Workflow bootstrap
    // ----------------------------------------------------------------

    /**
     * Bootstraps the complete billing workflow and returns the first
     * resident invoice id created by the finalize step.
     *
     * <p>Steps covered: meter readings & consumption → tiered tariff billing
     * engine + shared-area cost allocation → billing cycle finalization with
     * invoice status {@code ISSUED}.
     */
    private Long runFullBillingWorkflow() throws Exception {
        // Step 1 — Active tiered tariff plan for the apartment
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

        // Step 2 — Open the billing cycle
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

        // Step 3 — Log meter readings (8 kL + 10 kL = 18 kL metered volume)
        logUsage("2024-07-10", "8000.00");
        logUsage("2024-07-20", "10000.00");

        // Step 4 — Record bulk water purchase (shared-area cost allocation)
        BulkPurchaseRequest purchaseReq = new BulkPurchaseRequest();
        purchaseReq.setPurchaseDate(LocalDate.of(2024, 7, 15));
        purchaseReq.setVolumeLiters(new BigDecimal("5000.00"));
        purchaseReq.setUnitCost(new BigDecimal("0.4000"));

        mockMvc.perform(post("/api/apartments/" + SEEDED_APARTMENT_ID + "/bulk-purchases")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(purchaseReq)))
                .andExpect(status().isCreated());

        // Step 5 — Finalize the billing cycle (invoices transition to ISSUED)
        mockMvc.perform(patch("/api/billing-cycles/" + cycleId + "/finalize")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.invoicesGenerated").value(greaterThanOrEqualTo(1)));

        // Step 6 — Locate the resident's issued invoice
        String residentInvoicesResp = mockMvc.perform(get("/api/invoices/resident")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andReturn().getResponse().getContentAsString();

        JsonNode invoicesNode = objectMapper.readTree(residentInvoicesResp);
        assertThat(invoicesNode.size()).isGreaterThanOrEqualTo(1);
        return invoicesNode.get(0).get("id").asLong();
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private void logUsage(String date, String liters) throws Exception {
        UsageLogRequest req = new UsageLogRequest();
        req.setHouseholdId(SEEDED_HOUSEHOLD_ID);
        req.setReadingDate(LocalDate.parse(date));
        req.setVolumeUsedLiters(new BigDecimal(liters));
        req.setSource(UsageSource.MANUAL);

        mockMvc.perform(post("/api/usage-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }
}
