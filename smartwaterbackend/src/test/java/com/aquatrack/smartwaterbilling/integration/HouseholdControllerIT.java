package com.aquatrack.smartwaterbilling.integration;

import com.aquatrack.smartwaterbilling.AbstractIT;
import com.aquatrack.smartwaterbilling.TestAuthHelper;
import com.aquatrack.smartwaterbilling.dto.apartment.ApartmentRequest;
import com.aquatrack.smartwaterbilling.dto.household.AssignResidentRequest;
import com.aquatrack.smartwaterbilling.dto.household.HouseholdRequest;
import com.aquatrack.smartwaterbilling.dto.household.MeterConfigRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for household endpoints.
 *
 * <p>Seeded data (from seed_test_admin.sql via {@link AbstractIT}):
 * <ul>
 *   <li>Apartment 100  "IT Test Towers"</li>
 *   <li>Household 100  flat "A-101"  (threshold 500L)</li>
 *   <li>Admin   user 100  it-admin@test.com</li>
 *   <li>Resident user 101 it-resident@test.com  (linked to household 100)</li>
 * </ul>
 */
@DisplayName("Household Controller — Integration Tests")
class HouseholdControllerIT extends AbstractIT {

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = TestAuthHelper.adminToken(mockMvc);
    }

    // ----------------------------------------------------------------
    // POST /api/households — success
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/households — ADMIN creates household, returns 201 with body")
    void createHousehold_asAdmin_returns201() throws Exception {
        HouseholdRequest req = validHouseholdRequest(SEEDED_APARTMENT_ID, "B-202");

        mockMvc.perform(post("/api/households")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.flatNumber").value("B-202"))
                .andExpect(jsonPath("$.apartmentId").value(SEEDED_APARTMENT_ID))
                .andExpect(jsonPath("$.dailyThresholdLiters").value(300.0));
    }

    // ----------------------------------------------------------------
    // GET /api/households/{id} — success: read back seeded household
    // ----------------------------------------------------------------

    @Test
    @DisplayName("GET /api/households/100 — returns 200 with seeded household data")
    void getHousehold_seededId_returns200() throws Exception {
        mockMvc.perform(get("/api/households/" + SEEDED_HOUSEHOLD_ID)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SEEDED_HOUSEHOLD_ID))
                .andExpect(jsonPath("$.flatNumber").value(SEEDED_FLAT_NUMBER))
                .andExpect(jsonPath("$.apartmentId").value(SEEDED_APARTMENT_ID));
    }

    // ----------------------------------------------------------------
    // POST /api/households → GET — full create+read flow
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/households → GET /api/households/{id} — full happy-path flow")
    void createThenGet_fullFlow() throws Exception {
        HouseholdRequest req = validHouseholdRequest(SEEDED_APARTMENT_ID, "C-303");

        // Create
        String body = mockMvc.perform(post("/api/households")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long createdId = objectMapper.readTree(body).get("id").asLong();

        // Read back
        mockMvc.perform(get("/api/households/" + createdId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdId))
                .andExpect(jsonPath("$.flatNumber").value("C-303"));
    }

    // ----------------------------------------------------------------
    // POST /api/households/{id}/assign-resident — success
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/households/{id}/assign-resident — ADMIN assigns resident, returns 200")
    void assignResident_asAdmin_returns200() throws Exception {
        // Create a new household to assign to (the seeded household already has a resident)
        HouseholdRequest householdReq = validHouseholdRequest(SEEDED_APARTMENT_ID, "D-404");
        String hhBody = mockMvc.perform(post("/api/households")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(householdReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long newHouseholdId = objectMapper.readTree(hhBody).get("id").asLong();

        // Register a fresh resident (no household yet)
        long ts = System.currentTimeMillis();
        String residentEmail = "assign-resident-" + ts + "@test.com";
        TestAuthHelper.obtainToken(mockMvc, residentEmail, "ResPass#123",
                com.aquatrack.smartwaterbilling.entity.enums.Role.RESIDENT,
                null, SEEDED_HOUSEHOLD_ID);  // temporary link; we'll reassign

        // Now assign that resident to the new household via the API
        AssignResidentRequest assignReq = new AssignResidentRequest();
        assignReq.setResidentEmail(SEEDED_RESIDENT_EMAIL); // use seeded resident

        mockMvc.perform(post("/api/households/" + newHouseholdId + "/assign-resident")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(newHouseholdId));
    }

    // ----------------------------------------------------------------
    // PATCH /api/households/{id}/meter-config — success
    // ----------------------------------------------------------------

    @Test
    @DisplayName("PATCH /api/households/{id}/meter-config — updates hasMeter and threshold, returns 200")
    void updateMeterConfig_asAdmin_returns200() throws Exception {
        MeterConfigRequest req = new MeterConfigRequest();
        req.setHasMeter(true);
        req.setDailyThresholdLiters(BigDecimal.valueOf(750));

        mockMvc.perform(patch("/api/households/" + SEEDED_HOUSEHOLD_ID + "/meter-config")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMeter").value(true))
                .andExpect(jsonPath("$.dailyThresholdLiters").value(750.0));
    }

    // ----------------------------------------------------------------
    // POST /api/households — unauthenticated → 401/403
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/households — no token returns 401 or 403")
    void createHousehold_unauthenticated_returns401or403() throws Exception {
        HouseholdRequest req = validHouseholdRequest(SEEDED_APARTMENT_ID, "Z-999");

        mockMvc.perform(post("/api/households")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is(anyOf(equalTo(401), equalTo(403))));
    }

    // ----------------------------------------------------------------
    // POST /api/households — blank flatNumber → 400 with fieldErrors
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/households — blank flatNumber returns 400 with fieldErrors")
    void createHousehold_blankFlatNumber_returns400() throws Exception {
        HouseholdRequest req = new HouseholdRequest();
        req.setApartmentId(SEEDED_APARTMENT_ID);
        req.setFlatNumber("");  // blank
        req.setOccupancyCount(1);
        req.setHasMeter(false);

        mockMvc.perform(post("/api/households")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("flatNumber")));
    }

    // ----------------------------------------------------------------
    // POST /api/households — null apartmentId → 400
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/households — null apartmentId returns 400")
    void createHousehold_nullApartmentId_returns400() throws Exception {
        HouseholdRequest req = new HouseholdRequest();
        // apartmentId is null
        req.setFlatNumber("E-505");
        req.setOccupancyCount(2);
        req.setHasMeter(false);

        mockMvc.perform(post("/api/households")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("apartmentId")));
    }

    // ----------------------------------------------------------------
    // GET /api/households/{id} — not found → 404
    // ----------------------------------------------------------------

    @Test
    @DisplayName("GET /api/households/99999 — not found returns 404")
    void getHousehold_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/households/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------
    // POST /api/households — duplicate flat → 409 Conflict
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/households — duplicate flatNumber in same apartment returns 409")
    void createHousehold_duplicateFlatNumber_returns409() throws Exception {
        // SEEDED_FLAT_NUMBER "A-101" already exists in apartment 100
        HouseholdRequest req = validHouseholdRequest(SEEDED_APARTMENT_ID, SEEDED_FLAT_NUMBER);

        mockMvc.perform(post("/api/households")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // ----------------------------------------------------------------
    // Helper
    // ----------------------------------------------------------------

    private static HouseholdRequest validHouseholdRequest(Long apartmentId, String flatNumber) {
        HouseholdRequest req = new HouseholdRequest();
        req.setApartmentId(apartmentId);
        req.setFlatNumber(flatNumber);
        req.setOccupancyCount(3);
        req.setHasMeter(true);
        req.setDailyThresholdLiters(BigDecimal.valueOf(300));
        return req;
    }
}
