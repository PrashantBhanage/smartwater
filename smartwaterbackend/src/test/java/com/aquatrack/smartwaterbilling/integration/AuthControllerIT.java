package com.aquatrack.smartwaterbilling.integration;

import com.aquatrack.smartwaterbilling.AbstractIT;
import com.aquatrack.smartwaterbilling.TestAuthHelper;
import com.aquatrack.smartwaterbilling.dto.auth.LoginRequest;
import com.aquatrack.smartwaterbilling.dto.auth.RegisterRequest;
import com.aquatrack.smartwaterbilling.entity.enums.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for authentication endpoints.
 *
 * <p>Seed data (from seed_test_admin.sql via {@link AbstractIT}):
 * <ul>
 *   <li>Apartment 100, Household 100</li>
 *   <li>Admin   : it-admin@test.com    / TestPass#1</li>
 *   <li>Resident: it-resident@test.com / TestPass#1</li>
 * </ul>
 */
@DisplayName("Auth Controller — Integration Tests")
class AuthControllerIT extends AbstractIT {

    @Autowired
    private ObjectMapper objectMapper;

    // ----------------------------------------------------------------
    // POST /api/auth/login — success (seeded admin)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/login — valid credentials returns 200 with JWT token")
    void login_validCredentials_returns200WithToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(SEEDED_ADMIN_EMAIL);
        req.setPassword(SEEDED_ADMIN_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.email").value(SEEDED_ADMIN_EMAIL));
    }

    // ----------------------------------------------------------------
    // POST /api/auth/login — invalid credentials → 401
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/login — wrong password returns 401")
    void login_wrongPassword_returns401() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(SEEDED_ADMIN_EMAIL);
        req.setPassword("WrongPassword!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login — non-existent user returns 401")
    void login_nonExistentUser_returns401() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("nobody@nothere.com");
        req.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ----------------------------------------------------------------
    // GET /api/auth/me — success with seeded admin token
    // ----------------------------------------------------------------

    @Test
    @DisplayName("GET /api/auth/me — valid token returns 200 with correct profile")
    void me_validToken_returns200WithProfile() throws Exception {
        String token = TestAuthHelper.adminToken(mockMvc);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(SEEDED_ADMIN_EMAIL))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.id").value(SEEDED_ADMIN_USER_ID));
    }

    // ----------------------------------------------------------------
    // GET /api/auth/me — without token → 401 or 403
    // ----------------------------------------------------------------

    @Test
    @DisplayName("GET /api/auth/me — no token returns 401 or 403")
    void me_withoutToken_returns401or403() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().is(anyOf(equalTo(401), equalTo(403))));
    }

    // ----------------------------------------------------------------
    // POST /api/auth/register — success: register a new RESIDENT
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/register — RESIDENT success returns 201 with JWT token")
    void register_resident_success_returns201() throws Exception {
        long ts = System.currentTimeMillis();
        RegisterRequest req = new RegisterRequest();
        req.setFullName("New Resident");
        req.setEmail("new-resident-" + ts + "@test.com");
        req.setPassword("Resident#Pass1");
        req.setRole(Role.RESIDENT);
        req.setHouseholdId(SEEDED_HOUSEHOLD_ID);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.role").value("RESIDENT"));
    }

    // ----------------------------------------------------------------
    // POST /api/auth/register — validation: invalid email
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/register — invalid email returns 400 with fieldErrors")
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Test User");
        req.setEmail("not-an-email");
        req.setPassword("password123");
        req.setRole(Role.ADMIN);
        req.setApartmentId(SEEDED_APARTMENT_ID);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("email")));
    }

    // ----------------------------------------------------------------
    // POST /api/auth/register — validation: short password
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/register — password < 8 chars returns 400 with fieldErrors")
    void register_shortPassword_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Test User");
        req.setEmail("valid@email.com");
        req.setPassword("short");  // < 8 chars
        req.setRole(Role.ADMIN);
        req.setApartmentId(SEEDED_APARTMENT_ID);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("password")));
    }

    // ----------------------------------------------------------------
    // POST /api/auth/register — ADMIN without apartmentId → 400 (business rule)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/register — ADMIN without apartmentId returns 400")
    void register_adminWithoutApartment_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Bad Admin");
        req.setEmail("bad-admin@test.com");
        req.setPassword("ValidPass1");
        req.setRole(Role.ADMIN);
        // No apartmentId

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ----------------------------------------------------------------
    // POST /api/auth/register — duplicate email → 409 Conflict
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/register — duplicate email returns 409 Conflict")
    void register_duplicateEmail_returns409() throws Exception {
        // The seeded admin email already exists
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Duplicate Admin");
        req.setEmail(SEEDED_ADMIN_EMAIL);
        req.setPassword("AnotherPass1");
        req.setRole(Role.ADMIN);
        req.setApartmentId(SEEDED_APARTMENT_ID);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }
}
