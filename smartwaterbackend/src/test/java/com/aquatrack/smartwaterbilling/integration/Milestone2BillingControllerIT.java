package com.aquatrack.smartwaterbilling.integration;

import com.aquatrack.smartwaterbilling.AbstractIT;
import com.aquatrack.smartwaterbilling.TestAuthHelper;
import com.aquatrack.smartwaterbilling.dto.billing.BillingCycleRequest;
import com.aquatrack.smartwaterbilling.dto.purchase.BulkPurchaseRequest;
import com.aquatrack.smartwaterbilling.dto.tariff.TariffPlanRequest;
import com.aquatrack.smartwaterbilling.dto.tariff.TariffPlanUpdateRequest;
import com.aquatrack.smartwaterbilling.dto.usage.UsageLogRequest;
import com.aquatrack.smartwaterbilling.entity.enums.UsageSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Milestone 2 APIs - Integration Tests")
class Milestone2BillingControllerIT extends AbstractIT {

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
    @DisplayName("Complete Milestone 2 flow - Bulk Purchase -> Log Usage -> Finalize -> Resident Invoices & Alerts")
    void milestone2_completeFlow() throws Exception {
        // 1. Create Tariff Plan
        TariffPlanRequest tariffReq = new TariffPlanRequest();
        tariffReq.setApartmentId(SEEDED_APARTMENT_ID);
        tariffReq.setTier1LimitKl(new BigDecimal("10.000"));
        tariffReq.setTier1Rate(new BigDecimal("20.0000"));
        tariffReq.setTier2Rate(new BigDecimal("35.0000"));
        tariffReq.setEffectiveFromDate(LocalDate.of(2024, 1, 1));

        String tariffResponse = mockMvc.perform(post("/api/tariff-plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tariffReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long tariffId = objectMapper.readTree(tariffResponse).get("id").asLong();

        // Test PATCH tariff plan
        TariffPlanUpdateRequest tariffPatch = new TariffPlanUpdateRequest();
        tariffPatch.setTier1Rate(new BigDecimal("22.0000"));
        mockMvc.perform(patch("/api/tariff-plans/" + tariffId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tariffPatch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier1Rate").value(22.0000));

        // 2. Open Billing Cycle
        BillingCycleRequest cycleReq = new BillingCycleRequest();
        cycleReq.setApartmentId(SEEDED_APARTMENT_ID);
        cycleReq.setCycleStartDate(LocalDate.of(2024, 6, 1));
        cycleReq.setCycleEndDate(LocalDate.of(2024, 6, 30));

        String cycleResponse = mockMvc.perform(post("/api/billing-cycles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cycleReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long cycleId = objectMapper.readTree(cycleResponse).get("id").asLong();

        // 3. Record Bulk Water Purchase
        BulkPurchaseRequest purchaseReq = new BulkPurchaseRequest();
        purchaseReq.setPurchaseDate(LocalDate.of(2024, 6, 15));
        purchaseReq.setVolumeLiters(new BigDecimal("10000.00"));
        purchaseReq.setUnitCost(new BigDecimal("0.5000")); // total cost = 5000.00

        mockMvc.perform(post("/api/apartments/" + SEEDED_APARTMENT_ID + "/bulk-purchases")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(purchaseReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.totalCost").value(5000.00));

        // Get Bulk Purchases list
        mockMvc.perform(get("/api/apartments/" + SEEDED_APARTMENT_ID + "/bulk-purchases")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("cycleStart", "2024-06-01")
                        .param("cycleEnd", "2024-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(5000.00))
                .andExpect(jsonPath("$.purchases", hasSize(1)));

        // 4. Log Water Usage (above threshold to generate alert)
        UsageLogRequest logReq = new UsageLogRequest();
        logReq.setHouseholdId(SEEDED_HOUSEHOLD_ID);
        logReq.setReadingDate(LocalDate.of(2024, 6, 10));
        logReq.setVolumeUsedLiters(new BigDecimal("1500.00")); // default threshold is 500
        logReq.setSource(UsageSource.MANUAL);

        mockMvc.perform(post("/api/usage-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usageStatus").value("RED"));

        // 5. Finalize Cycle
        mockMvc.perform(post("/api/billing-cycles/" + cycleId + "/finalize")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoicesGenerated").value(1))
                .andExpect(jsonPath("$.totalSharedAllocation").value(5000.00));

        // 6. Admin gets all invoices
        mockMvc.perform(get("/api/billing-cycles/" + cycleId + "/invoices")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].householdId").value(SEEDED_HOUSEHOLD_ID))
                .andExpect(jsonPath("$[0].sharedCostAllocation").value(5000.00));

        // 7. Resident gets their own invoices for the cycle
        mockMvc.perform(get("/api/billing-cycles/" + cycleId + "/invoices")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // 8. Resident checks all their invoices
        mockMvc.perform(get("/api/residents/my-invoices")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // 9. Admin checks recent apartment alerts
        mockMvc.perform(get("/api/apartments/" + SEEDED_APARTMENT_ID + "/alerts")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // 10. Resident checks their own household alerts
        mockMvc.perform(get("/api/residents/my-alerts")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }
}
