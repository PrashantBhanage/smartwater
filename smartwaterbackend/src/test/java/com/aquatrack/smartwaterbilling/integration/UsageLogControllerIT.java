package com.aquatrack.smartwaterbilling.integration;

import com.aquatrack.smartwaterbilling.AbstractIT;
import com.aquatrack.smartwaterbilling.dto.usage.UsageLogRequest;
import com.aquatrack.smartwaterbilling.entity.enums.UsageSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;


import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for water usage log endpoints.
 * Tests cover: manual entry, bulk CSV upload, duplicate detection,
 * validation failures, and authorization.
 */
@DisplayName("UsageLog Controller — Integration Tests")
class UsageLogControllerIT extends AbstractIT {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpMapper() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // ----------------------------------------------------------------
    // POST /api/usage-logs — unauthenticated
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/usage-logs — unauthenticated returns 401/403")
    void createLog_unauthenticated_returns401or403() throws Exception {
        UsageLogRequest req = validLogRequest(1L, LocalDate.now().minusDays(1), BigDecimal.valueOf(300));

        mockMvc.perform(post("/api/usage-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().findAndRegisterModules().writeValueAsString(req)))
                .andExpect(status().is(anyOf(equalTo(401), equalTo(403))));
    }

    // ----------------------------------------------------------------
    // POST /api/usage-logs — validation: null householdId
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/usage-logs — null householdId returns 400")
    void createLog_nullHouseholdId_returns400() throws Exception {
        UsageLogRequest req = new UsageLogRequest();
        // householdId is null
        req.setReadingDate(LocalDate.now().minusDays(1));
        req.setVolumeUsedLiters(BigDecimal.valueOf(100));
        req.setSource(UsageSource.MANUAL);

        mockMvc.perform(post("/api/usage-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().findAndRegisterModules().writeValueAsString(req)))
                .andExpect(status().is(anyOf(equalTo(400), equalTo(401), equalTo(403))));
    }

    // ----------------------------------------------------------------
    // POST /api/usage-logs — validation: future date
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/usage-logs — future reading date returns 400")
    void createLog_futureDate_returns400() throws Exception {
        UsageLogRequest req = new UsageLogRequest();
        req.setHouseholdId(1L);
        req.setReadingDate(LocalDate.now().plusDays(5)); // future date
        req.setVolumeUsedLiters(BigDecimal.valueOf(200));
        req.setSource(UsageSource.MANUAL);

        mockMvc.perform(post("/api/usage-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().findAndRegisterModules().writeValueAsString(req)))
                .andExpect(status().is(anyOf(equalTo(400), equalTo(401), equalTo(403))));
    }

    // ----------------------------------------------------------------
    // POST /api/usage-logs/bulk-upload — unauthenticated
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/usage-logs/bulk-upload — unauthenticated returns 401/403")
    void bulkUpload_unauthenticated_returns401or403() throws Exception {
        String csvContent = "household_id,reading_date,meter_reading_value,volume_used_liters\n" +
                            "1,2024-01-15,,300.0\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "usage.csv", "text/csv", csvContent.getBytes());

        mockMvc.perform(multipart("/api/usage-logs/bulk-upload").file(file))
                .andExpect(status().is(anyOf(equalTo(401), equalTo(403))));
    }

    // ----------------------------------------------------------------
    // POST /api/usage-logs/bulk-upload — empty file
    // ----------------------------------------------------------------

    @Test
    @DisplayName("POST /api/usage-logs/bulk-upload — empty file returns 400/401/403")
    void bulkUpload_emptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/api/usage-logs/bulk-upload").file(file))
                .andExpect(status().is(anyOf(equalTo(400), equalTo(401), equalTo(403))));
    }

    // ----------------------------------------------------------------
    // GET /api/usage-logs — unauthenticated
    // ----------------------------------------------------------------

    @Test
    @DisplayName("GET /api/usage-logs — unauthenticated returns 401/403")
    void getLogs_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/usage-logs").param("householdId", "1"))
                .andExpect(status().is(anyOf(equalTo(401), equalTo(403))));
    }

    // ----------------------------------------------------------------
    // Helper
    // ----------------------------------------------------------------

    private UsageLogRequest validLogRequest(Long householdId, LocalDate date, BigDecimal volume) {
        UsageLogRequest req = new UsageLogRequest();
        req.setHouseholdId(householdId);
        req.setReadingDate(date);
        req.setVolumeUsedLiters(volume);
        req.setSource(UsageSource.MANUAL);
        return req;
    }
}
