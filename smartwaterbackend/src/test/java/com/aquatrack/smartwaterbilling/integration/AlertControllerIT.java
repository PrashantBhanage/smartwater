package com.aquatrack.smartwaterbilling.integration;

import com.aquatrack.smartwaterbilling.AbstractIT;
import com.aquatrack.smartwaterbilling.TestAuthHelper;
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

/**
 * Integration tests for threshold alerts created when usage exceeds
 * the household daily threshold (seeded household 100 → 500 L).
 */
@DisplayName("Alert Controller — Integration Tests")
class AlertControllerIT extends AbstractIT {

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = TestAuthHelper.obtainAdminToken(mockMvc);
    }

    @Test
    @DisplayName("Usage above threshold creates THRESHOLD_EXCEEDED alert")
    void usageAboveThreshold_createsAlert() throws Exception {
        UsageLogRequest req = new UsageLogRequest();
        req.setHouseholdId(SEEDED_HOUSEHOLD_ID);
        req.setReadingDate(LocalDate.of(2024, 5, 1));
        req.setVolumeUsedLiters(new BigDecimal("600")); // > 500 threshold
        req.setSource(UsageSource.MANUAL);

        mockMvc.perform(post("/api/usage-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/alerts")
                        .param("householdId", SEEDED_HOUSEHOLD_ID.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].alertType").value("THRESHOLD_EXCEEDED"))
                .andExpect(jsonPath("$[0].householdId").value(SEEDED_HOUSEHOLD_ID))
                .andExpect(jsonPath("$[0].usageLiters").value(600.0));
    }

    @Test
    @DisplayName("Usage at/below threshold creates no alert")
    void usageAtThreshold_noAlert() throws Exception {
        UsageLogRequest req = new UsageLogRequest();
        req.setHouseholdId(SEEDED_HOUSEHOLD_ID);
        req.setReadingDate(LocalDate.of(2024, 5, 2));
        req.setVolumeUsedLiters(new BigDecimal("500")); // == threshold → no alert
        req.setSource(UsageSource.MANUAL);

        mockMvc.perform(post("/api/usage-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/alerts")
                        .param("householdId", SEEDED_HOUSEHOLD_ID.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/alerts — unauthenticated returns 401 or 403")
    void listAlerts_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/alerts").param("householdId", "100"))
                .andExpect(status().is(anyOf(equalTo(401), equalTo(403))));
    }
}
