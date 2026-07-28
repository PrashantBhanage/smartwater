package com.aquatrack.smartwaterbilling.integration;

import com.aquatrack.smartwaterbilling.AbstractIT;
import com.aquatrack.smartwaterbilling.TestAuthHelper;
import com.aquatrack.smartwaterbilling.dto.billing.BillingCycleRequest;
import com.aquatrack.smartwaterbilling.dto.household.HouseholdRequest;
import com.aquatrack.smartwaterbilling.dto.purchase.PurchaseRequest;
import com.aquatrack.smartwaterbilling.dto.tariff.TariffPlanRequest;
import com.aquatrack.smartwaterbilling.dto.usage.UsageLogRequest;
import com.aquatrack.smartwaterbilling.entity.enums.PurchaseSource;
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

/**
 * Integration tests for Module 2 billing: tariff plans, purchases,
 * billing-cycle open → finalize → archive, and invoice generation.
 */
@DisplayName("Billing Module — Integration Tests")
class BillingModuleIT extends AbstractIT {

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = TestAuthHelper.obtainAdminToken(mockMvc);
    }

    // ----------------------------------------------------------------
    // Tariff plans
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/tariff-plans — ADMIN creates plan, returns 201")
    void createTariffPlan_returns201() throws Exception {
        TariffPlanRequest req = tariffRequest();

        mockMvc.perform(post("/api/tariff-plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.apartmentId").value(SEEDED_APARTMENT_ID))
                .andExpect(jsonPath("$.tier1LimitKl").value(10.0));
    }

    // ----------------------------------------------------------------
    // Billing cycle open
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/billing-cycles — opens OPEN cycle, returns 201")
    void openBillingCycle_returns201() throws Exception {
        BillingCycleRequest req = cycleRequest("2024-06-01", "2024-06-30");

        mockMvc.perform(post("/api/billing-cycles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.apartmentId").value(SEEDED_APARTMENT_ID));
    }

    @Test
    @DisplayName("POST /api/billing-cycles — second OPEN cycle returns 409")
    void openSecondCycle_returns409() throws Exception {
        openCycle("2024-06-01", "2024-06-30");

        mockMvc.perform(post("/api/billing-cycles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                cycleRequest("2024-07-01", "2024-07-31"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/billing-cycles — end before start returns 400")
    void openCycle_invalidDates_returns400() throws Exception {
        mockMvc.perform(post("/api/billing-cycles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                cycleRequest("2024-06-30", "2024-06-01"))))
                .andExpect(status().isBadRequest());
    }

    // ----------------------------------------------------------------
    // Purchases
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/purchases — records purchase on OPEN cycle, returns 201")
    void recordPurchase_returns201() throws Exception {
        Long cycleId = openCycle("2024-06-01", "2024-06-30");
        PurchaseRequest req = purchaseRequest(cycleId);

        mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.cycleId").value(cycleId))
                .andExpect(jsonPath("$.source").value("TANKER"))
                .andExpect(jsonPath("$.totalCost").value(5000.0)); // 10 × 500
    }

    @Test
    @DisplayName("POST /api/purchases — blank volume validation returns 400")
    void recordPurchase_invalidVolume_returns400() throws Exception {
        Long cycleId = openCycle("2024-06-01", "2024-06-30");
        PurchaseRequest req = purchaseRequest(cycleId);
        req.setVolumePurchasedKl(BigDecimal.ZERO);

        mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ----------------------------------------------------------------
    // Finalize + archive full flow
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Finalize OPEN cycle → invoices generated; archive → ARCHIVED")
    void finalizeThenArchive_fullFlow() throws Exception {
        createTariff();
        Long cycleId = openCycle("2024-06-01", "2024-06-30");

        // Shared purchase cost = 10 kL × 100 = 1000
        PurchaseRequest purchase = purchaseRequest(cycleId);
        purchase.setUnitCost(new BigDecimal("100.0000"));
        mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(purchase)))
                .andExpect(status().isCreated());

        // Metered usage for seeded household: 5000 L = 5 kL → base = 5 × 20 = 100
        logUsage(SEEDED_HOUSEHOLD_ID, LocalDate.of(2024, 6, 15), new BigDecimal("5000"));

        // Finalize
        mockMvc.perform(patch("/api/billing-cycles/" + cycleId + "/finalize")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.invoicesGenerated").value(1));

        // Invoices
        mockMvc.perform(get("/api/billing-cycles/" + cycleId + "/invoices")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].householdId").value(SEEDED_HOUSEHOLD_ID))
                .andExpect(jsonPath("$[0].baseCharge").value(100.0))
                .andExpect(jsonPath("$[0].sharedAllocation").value(1000.0)) // sole metered HH
                .andExpect(jsonPath("$[0].totalAmount").value(1100.0))
                .andExpect(jsonPath("$[0].status").value("ISSUED"));

        // Archive
        mockMvc.perform(patch("/api/billing-cycles/" + cycleId + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("Finalize without tariff plan returns 400")
    void finalize_withoutTariff_returns400() throws Exception {
        Long cycleId = openCycle("2024-06-01", "2024-06-30");

        mockMvc.perform(patch("/api/billing-cycles/" + cycleId + "/finalize")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Archive OPEN cycle returns 400")
    void archiveOpenCycle_returns400() throws Exception {
        Long cycleId = openCycle("2024-06-01", "2024-06-30");

        mockMvc.perform(patch("/api/billing-cycles/" + cycleId + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Finalize with metered + unmetered households splits shared cost by headcount pools")
    void finalize_mixedHouseholds_splitsSharedCost() throws Exception {
        createTariff();
        Long cycleId = openCycle("2024-06-01", "2024-06-30");

        // Add unmetered household with area
        HouseholdRequest hhReq = new HouseholdRequest();
        hhReq.setApartmentId(SEEDED_APARTMENT_ID);
        hhReq.setFlatNumber("B-202");
        hhReq.setOccupancyCount(2);
        hhReq.setHasMeter(false);
        hhReq.setAreaSqft(new BigDecimal("1000"));
        mockMvc.perform(post("/api/households")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hhReq)))
                .andExpect(status().isCreated());

        PurchaseRequest purchase = purchaseRequest(cycleId);
        purchase.setVolumePurchasedKl(new BigDecimal("10.000"));
        purchase.setUnitCost(new BigDecimal("100.0000")); // total 1000
        mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(purchase)))
                .andExpect(status().isCreated());

        logUsage(SEEDED_HOUSEHOLD_ID, LocalDate.of(2024, 6, 10), new BigDecimal("2000"));

        mockMvc.perform(patch("/api/billing-cycles/" + cycleId + "/finalize")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoicesGenerated").value(2));

        // 2 households → each pool 500. Metered gets 500; unmetered gets 500.
        mockMvc.perform(get("/api/billing-cycles/" + cycleId + "/invoices")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].sharedAllocation", containsInAnyOrder(500.0, 500.0)));
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private TariffPlanRequest tariffRequest() {
        TariffPlanRequest req = new TariffPlanRequest();
        req.setApartmentId(SEEDED_APARTMENT_ID);
        req.setTier1LimitKl(new BigDecimal("10.000"));
        req.setTier1Rate(new BigDecimal("20.0000"));
        req.setTier2Rate(new BigDecimal("35.0000"));
        req.setEffectiveFromDate(LocalDate.of(2024, 1, 1));
        return req;
    }

    private void createTariff() throws Exception {
        mockMvc.perform(post("/api/tariff-plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tariffRequest())))
                .andExpect(status().isCreated());
    }

    private BillingCycleRequest cycleRequest(String start, String end) {
        BillingCycleRequest req = new BillingCycleRequest();
        req.setApartmentId(SEEDED_APARTMENT_ID);
        req.setCycleStartDate(LocalDate.parse(start));
        req.setCycleEndDate(LocalDate.parse(end));
        return req;
    }

    private Long openCycle(String start, String end) throws Exception {
        String body = mockMvc.perform(post("/api/billing-cycles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cycleRequest(start, end))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private PurchaseRequest purchaseRequest(Long cycleId) {
        PurchaseRequest req = new PurchaseRequest();
        req.setApartmentId(SEEDED_APARTMENT_ID);
        req.setCycleId(cycleId);
        req.setVolumePurchasedKl(new BigDecimal("10.000"));
        req.setUnitCost(new BigDecimal("500.0000"));
        req.setPurchaseDate(LocalDate.of(2024, 6, 5));
        req.setSource(PurchaseSource.TANKER);
        return req;
    }

    private void logUsage(Long householdId, LocalDate date, BigDecimal liters) throws Exception {
        UsageLogRequest req = new UsageLogRequest();
        req.setHouseholdId(householdId);
        req.setReadingDate(date);
        req.setVolumeUsedLiters(liters);
        req.setSource(UsageSource.MANUAL);
        mockMvc.perform(post("/api/usage-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }
}
