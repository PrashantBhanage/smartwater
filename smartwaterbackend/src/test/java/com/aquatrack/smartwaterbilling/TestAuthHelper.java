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
 * Registers a user (if needed) and returns the JWT Bearer token string.
 */
public class TestAuthHelper {

    private TestAuthHelper() {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Registers a user and returns the JWT token.
     * If the user already exists (409), falls back to login.
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

        if (result.getResponse().getStatus() == 409) {
            // Already registered — login instead
            return login(mockMvc, email, password);
        }

        AuthResponse auth = MAPPER.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        return auth.getToken();
    }

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
