package com.aquatrack.smartwaterbilling;

import com.aquatrack.smartwaterbilling.dto.auth.AuthResponse;
import com.aquatrack.smartwaterbilling.dto.auth.LoginRequest;
import com.aquatrack.smartwaterbilling.dto.auth.RegisterRequest;
import com.aquatrack.smartwaterbilling.entity.enums.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared test helper for obtaining JWT tokens in integration tests.
 *
 * <h2>Seeded user shortcut</h2>
 * {@link #adminToken(MockMvc)} and {@link #residentToken(MockMvc)} login with
 * the credentials seeded by {@code seed_test_admin.sql} — no registration
 * step needed, no chicken-and-egg bootstrap problem.
 */
public class TestAuthHelper {

    private TestAuthHelper() {}

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    // ----------------------------------------------------------------
    // Convenience helpers for the seeded test users
    // ----------------------------------------------------------------

    /** Returns a JWT for the seeded ADMIN user (it-admin@test.com / TestPass#1). */
    public static String obtainAdminToken(MockMvc mockMvc) throws Exception {
        return login(mockMvc,
                AbstractIT.SEEDED_ADMIN_EMAIL,
                AbstractIT.SEEDED_ADMIN_PASSWORD);
    }

    /** @see #obtainAdminToken(MockMvc) */
    public static String adminToken(MockMvc mockMvc) throws Exception {
        return obtainAdminToken(mockMvc);
    }

    /** Returns a JWT for the seeded RESIDENT user (it-resident@test.com / TestPass#1). */
    public static String residentToken(MockMvc mockMvc) throws Exception {
        return login(mockMvc,
                AbstractIT.SEEDED_RESIDENT_EMAIL,
                AbstractIT.SEEDED_RESIDENT_PASSWORD);
    }

    // ----------------------------------------------------------------
    // Generic helpers
    // ----------------------------------------------------------------

    /**
     * Registers a user and returns the JWT token.
     * If the user already exists (409 Conflict), falls back to login.
     */
    public static String obtainToken(MockMvc mockMvc, String email, String password,
                                      Role role, Long apartmentId, Long householdId)
            throws Exception {

        RegisterRequest reg = new RegisterRequest();
        reg.setFullName("Test " + role.name());
        reg.setEmail(email);
        reg.setPassword(password);
        reg.setRole(role);
        reg.setApartmentId(apartmentId);
        reg.setHouseholdId(householdId);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(reg)))
                .andReturn();

        int status = result.getResponse().getStatus();
        if (status == 409 || status == 201) {
            // 409 = already registered, fall through to login
            // 201 = registered successfully, token is in the body
            if (status == 201) {
                AuthResponse auth = MAPPER.readValue(
                        result.getResponse().getContentAsString(), AuthResponse.class);
                return auth.getToken();
            }
            return login(mockMvc, email, password);
        }

        throw new IllegalStateException(
                "Unexpected status " + status + " during test user registration. Body: " +
                result.getResponse().getContentAsString());
    }

    /** Logs in with the given credentials and returns the JWT token. */
    public static String login(MockMvc mockMvc, String email, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth = MAPPER.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        return auth.getToken();
    }
}
