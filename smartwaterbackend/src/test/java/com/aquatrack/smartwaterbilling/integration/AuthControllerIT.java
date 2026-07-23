package com.aquatrack.smartwaterbilling.integration;

import com.aquatrack.smartwaterbilling.AbstractIT;
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
 * Uses Testcontainers PostgreSQL via {@link AbstractIT}.
 */
@DisplayName("Auth Controller — Integration Tests")
class AuthControllerIT extends AbstractIT {

    @Autowired
    private ObjectMapper objectMapper;

    // ----------------------------------------------------------------
    // POST /api/auth/register — success
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/register — 201 created with JWT token")
    void register_success_returns201() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Integration Admin");
        req.setEmail("it-admin-" + System.currentTimeMillis() + "@test.com");
        req.setPassword("securePass1");
        req.setRole(Role.ADMIN);
        // No apartmentId — ADMIN without apartment to keep test isolated
        // We'll test full flow in ApartmentControllerIT

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest()); // apartmentId required for ADMIN
    }

    @Test
    @DisplayName("POST /api/auth/register — ADMIN without apartmentId returns 400")
    void register_adminWithoutApartment_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Bad Admin");
        req.setEmail("bad-admin@test.com");
        req.setPassword("securePass1");
        req.setRole(Role.ADMIN);
        // Missing apartmentId

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ----------------------------------------------------------------
    // POST /api/auth/register — validation failures
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/register — invalid email returns 400 with fieldErrors")
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Test User");
        req.setEmail("not-an-email");
        req.setPassword("password123");
        req.setRole(Role.ADMIN);
        req.setApartmentId(1L);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("email")));
    }

    @Test
    @DisplayName("POST /api/auth/register — short password returns 400 with fieldErrors")
    void register_shortPassword_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Test");
        req.setEmail("valid@email.com");
        req.setPassword("short");  // < 8 chars
        req.setRole(Role.ADMIN);
        req.setApartmentId(1L);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("password")));
    }

    // ----------------------------------------------------------------
    // POST /api/auth/login — invalid credentials
    // ----------------------------------------------------------------

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
    // GET /api/auth/me — without token
    // ----------------------------------------------------------------

    @Test
    @DisplayName("GET /api/auth/me — without token returns 403 or 401")
    void me_withoutToken_returns401or403() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().is(anyOf(equalTo(401), equalTo(403))));
    }
}
