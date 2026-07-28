package com.aquatrack.smartwaterbilling.integration;

import com.aquatrack.smartwaterbilling.AbstractIT;
import com.aquatrack.smartwaterbilling.TestAuthHelper;
import com.aquatrack.smartwaterbilling.dto.apartment.ApartmentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for apartment endpoints.
 *
 * <p>All authenticated tests use the seeded admin (it-admin@test.com / TestPass#1).
 * Apartment 100 is pre-seeded (see seed_test_admin.sql via {@link AbstractIT}).
 */
@DisplayName("Apartment Controller — Integration Tests")
class ApartmentControllerIT extends AbstractIT {

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = TestAuthHelper.adminToken(mockMvc);
    }

    // ----------------------------------------------------------------
    // POST /api/apartments — success (ADMIN token)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/apartments — ADMIN creates apartment, returns 201 with body")
    void createApartment_asAdmin_returns201() throws Exception {
        long ts = System.currentTimeMillis();
        ApartmentRequest req = validApartmentRequest("New Towers " + ts,
                "newcontact-" + ts + "@test.com");

        mockMvc.perform(post("/api/apartments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("New Towers " + ts))
                .andExpect(jsonPath("$.address").value("123 Integration St"))
                .andExpect(jsonPath("$.totalHouseholds").value(50));
    }

    // ----------------------------------------------------------------
    // GET /api/apartments/{id} — success: read back the seeded apartment
    // ----------------------------------------------------------------

    @Test
    @DisplayName("GET /api/apartments/100 — returns 200 with seeded apartment data")
    void getApartment_seededId_returns200() throws Exception {
        mockMvc.perform(get("/api/apartments/" + SEEDED_APARTMENT_ID)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SEEDED_APARTMENT_ID))
                .andExpect(jsonPath("$.name").value("IT Test Towers"))
                .andExpect(jsonPath("$.adminContact").value(SEEDED_ADMIN_EMAIL));
    }

    // ----------------------------------------------------------------
    // GET /api/apartments/{id} — full create → read-back flow
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/apartments → GET /api/apartments/{id} — full happy-path flow")
    void createThenGet_fullFlow() throws Exception {
        long ts = System.currentTimeMillis();
        ApartmentRequest req = validApartmentRequest("Flow Towers " + ts,
                "flow-" + ts + "@contact.com");

        // Create
        String body = mockMvc.perform(post("/api/apartments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long createdId = objectMapper.readTree(body).get("id").asLong();

        // Read back
        mockMvc.perform(get("/api/apartments/" + createdId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdId))
                .andExpect(jsonPath("$.name").value("Flow Towers " + ts));
    }

    // ----------------------------------------------------------------
    // POST /api/apartments — unauthenticated → 401 or 403
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/apartments — no token returns 401 or 403")
    void createApartment_unauthenticated_returns401or403() throws Exception {
        ApartmentRequest req = validApartmentRequest("Unauth Apt", "unauth@test.com");

        mockMvc.perform(post("/api/apartments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is(anyOf(equalTo(401), equalTo(403))));
    }

    // ----------------------------------------------------------------
    // POST /api/apartments — RESIDENT token → 403 Forbidden
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/apartments — RESIDENT token returns 403")
    void createApartment_residentRole_returns403() throws Exception {
        String residentToken = TestAuthHelper.residentToken(mockMvc);
        ApartmentRequest req = validApartmentRequest("Resident Apt", "resident-apt@test.com");

        mockMvc.perform(post("/api/apartments")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ----------------------------------------------------------------
    // POST /api/apartments — blank name → 400 with fieldErrors
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/apartments — blank name returns 400 with fieldErrors")
    void createApartment_blankName_returns400() throws Exception {
        ApartmentRequest req = new ApartmentRequest();
        req.setName("");   // blank
        req.setAddress("123 Main St");
        req.setTotalHouseholds(10);
        req.setAdminContact("contact@test.com");

        mockMvc.perform(post("/api/apartments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("name")));
    }

    // ----------------------------------------------------------------
    // POST /api/apartments — invalid adminContact email → 400
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/apartments — invalid adminContact email returns 400")
    void createApartment_invalidAdminContact_returns400() throws Exception {
        ApartmentRequest req = new ApartmentRequest();
        req.setName("Valid Name");
        req.setAddress("Valid Address");
        req.setTotalHouseholds(5);
        req.setAdminContact("not-an-email");

        mockMvc.perform(post("/api/apartments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("adminContact")));
    }

    // ----------------------------------------------------------------
    // GET /api/apartments/{id} — not found → 404
    // ----------------------------------------------------------------

    @Test
    @DisplayName("GET /api/apartments/99999 — not found returns 404")
    void getApartment_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/apartments/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------
    // Helper
    // ----------------------------------------------------------------

    private static ApartmentRequest validApartmentRequest(String name, String contact) {
        ApartmentRequest req = new ApartmentRequest();
        req.setName(name);
        req.setAddress("123 Integration St");
        req.setTotalHouseholds(50);
        req.setAdminContact(contact);
        return req;
    }
}
