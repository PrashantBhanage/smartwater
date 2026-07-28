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
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for water usage log endpoints.
 *
 * <p>Covers:
 * <ul>
 *   <li>POST /api/usage-logs (manual entry) — success, duplicate (409), validation (400)</li>
 *   <li>GET  /api/usage-logs — authenticated success</li>
 *   <li>POST /api/usage-logs/bulk-upload — three scenarios:
 *     <ol>
 *       <li><strong>Clean batch</strong>: 2 valid rows, no prior data → inserted=2, skipped=0, failed=0</li>
 *       <li><strong>Duplicate batch</strong>: same 2 rows again → inserted=0, skipped=2, failed=0</li>
 *       <li><strong>Invalid-rows batch</strong>: 3 rows, all bad → inserted=0, failed=3, failedRows with reasons</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <p>Seeded: Apartment 100, Household 100 (threshold 500L), Admin it-admin@test.com.
 */
@DisplayName("UsageLog Controller — Integration Tests")
class UsageLogControllerIT extends AbstractIT {

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String residentToken;

    // CSV dates far in the past to avoid future-date rejection
    private static final String DATE_1 = "2024-01-10";
    private static final String DATE_2 = "2024-01-11";

    @BeforeEach
    void setUp() throws Exception {
        adminToken    = TestAuthHelper.adminToken(mockMvc);
        residentToken = TestAuthHelper.residentToken(mockMvc);
    }

    // ================================================================
    // POST /api/usage-logs  — manual entry
    // ================================================================

    @Test
    @DisplayName("POST /api/usage-logs — manual entry success returns 201 with status GREEN")
    void createLog_manualSuccess_returns201() throws Exception {
        UsageLogRequest req = manualLogRequest(SEEDED_HOUSEHOLD_ID,
                LocalDate.of(2024, 3, 1), BigDecimal.valueOf(300)); // 300 ≤ 500 → GREEN

        mockMvc.perform(post("/api/usage-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.householdId").value(SEEDED_HOUSEHOLD_ID))
                .andExpect(jsonPath("$.volumeUsedLiters").value(300.0))
                .andExpect(jsonPath("$.usageStatus").value("GREEN"))
                .andExpect(jsonPath("$.source").value("MANUAL"));
    }

    @Test
    @DisplayName("POST /api/usage-logs — volume > 1.5×threshold returns YELLOW status")
    void createLog_yellowStatus() throws Exception {
        UsageLogRequest req = manualLogRequest(SEEDED_HOUSEHOLD_ID,
                LocalDate.of(2024, 3, 2), BigDecimal.valueOf(600)); // 500 < 600 < 750 → YELLOW

        mockMvc.perform(post("/api/usage-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usageStatus").value("YELLOW"));
    }

    @Test
    @DisplayName("POST /api/usage-logs — volume >= 1.5×threshold returns RED status")
    void createLog_redStatus() throws Exception {
        UsageLogRequest req = manualLogRequest(SEEDED_HOUSEHOLD_ID,
                LocalDate.of(2024, 3, 3), BigDecimal.valueOf(750)); // 750 >= 750 → RED

        mockMvc.perform(post("/api/usage-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usageStatus").value("RED"));
    }

    @Test
    @DisplayName("POST /api/usage-logs — duplicate (same household + date) returns 409")
    void createLog_duplicate_returns409() throws Exception {
        LocalDate date = LocalDate.of(2024, 4, 1);
        UsageLogRequest req = manualLogRequest(SEEDED_HOUSEHOLD_ID, date, BigDecimal.valueOf(200));

        // First insert — succeeds
        mockMvc.perform(post("/api/usage-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Duplicate — same household, same date
        mockMvc.perform(post("/api/usage-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/usage-logs — unauthenticated returns 401 or 403")
    void createLog_unauthenticated_returns401or403() throws Exception {
        UsageLogRequest req = manualLogRequest(SEEDED_HOUSEHOLD_ID,
                LocalDate.now().minusDays(1), BigDecimal.valueOf(100));

        mockMvc.perform(post("/api/usage-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is(anyOf(equalTo(401), equalTo(403))));
    }

    @Test
    @DisplayName("POST /api/usage-logs — null householdId returns 400 with fieldErrors")
    void createLog_nullHouseholdId_returns400() throws Exception {
        UsageLogRequest req = new UsageLogRequest();
        // householdId is null
        req.setReadingDate(LocalDate.now().minusDays(1));
        req.setVolumeUsedLiters(BigDecimal.valueOf(100));
        req.setSource(UsageSource.MANUAL);

        mockMvc.perform(post("/api/usage-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("householdId")));
    }

    @Test
    @DisplayName("POST /api/usage-logs — future reading date returns 400")
    void createLog_futureDate_returns400() throws Exception {
        UsageLogRequest req = manualLogRequest(SEEDED_HOUSEHOLD_ID,
                LocalDate.now().plusDays(5), BigDecimal.valueOf(200));

        mockMvc.perform(post("/api/usage-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ================================================================
    // GET /api/usage-logs
    // ================================================================

    @Test
    @DisplayName("GET /api/usage-logs?householdId=100 — authenticated returns 200 with list")
    void getLogs_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/api/usage-logs")
                        .param("householdId", SEEDED_HOUSEHOLD_ID.toString())
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", instanceOf(java.util.List.class)));
    }

    @Test
    @DisplayName("GET /api/usage-logs — unauthenticated returns 401 or 403")
    void getLogs_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/usage-logs").param("householdId", "100"))
                .andExpect(status().is(anyOf(equalTo(401), equalTo(403))));
    }

    // ================================================================
    // POST /api/usage-logs/bulk-upload
    // Scenario 1 — CLEAN BATCH
    // ================================================================

    @Test
    @DisplayName("Bulk upload — clean batch: 2 valid rows → inserted=2, skipped=0, failed=0")
    void bulkUpload_cleanBatch_insertsAllRows() throws Exception {
        String csv = "household_id,reading_date,meter_reading_value,volume_used_liters\n" +
                     SEEDED_HOUSEHOLD_ID + "," + DATE_1 + ",1000.000,300.00\n" +
                     SEEDED_HOUSEHOLD_ID + "," + DATE_2 + ",1300.000,450.00\n";

        MockMultipartFile file = csvFile(csv);

        mockMvc.perform(multipart("/api/usage-logs/bulk-upload")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsProcessed").value(2))
                .andExpect(jsonPath("$.rowsInserted").value(2))
                .andExpect(jsonPath("$.rowsSkipped").value(0))
                .andExpect(jsonPath("$.rowsFailed").value(0))
                .andExpect(jsonPath("$.failedRows", hasSize(0)));
    }

    // ================================================================
    // Scenario 2 — DUPLICATE BATCH
    // (same CSV uploaded a second time — all rows already exist)
    // ================================================================

    @Test
    @DisplayName("Bulk upload — duplicate batch: re-upload same rows → inserted=0, skipped=2, failed=0")
    void bulkUpload_duplicateBatch_skipsAllRows() throws Exception {
        String csv = "household_id,reading_date,meter_reading_value,volume_used_liters\n" +
                     SEEDED_HOUSEHOLD_ID + "," + DATE_1 + ",1000.000,300.00\n" +
                     SEEDED_HOUSEHOLD_ID + "," + DATE_2 + ",1300.000,450.00\n";

        MockMultipartFile file = csvFile(csv);

        // First upload — establishes the data
        mockMvc.perform(multipart("/api/usage-logs/bulk-upload")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsInserted").value(2));

        // Second upload — same rows must be skipped as duplicates
        MockMultipartFile fileAgain = csvFile(csv);
        mockMvc.perform(multipart("/api/usage-logs/bulk-upload")
                        .file(fileAgain)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsProcessed").value(2))
                .andExpect(jsonPath("$.rowsInserted").value(0))
                .andExpect(jsonPath("$.rowsSkipped").value(2))
                .andExpect(jsonPath("$.rowsFailed").value(0))
                .andExpect(jsonPath("$.failedRows", hasSize(0)));
    }

    // ================================================================
    // Scenario 3 — INVALID ROWS BATCH
    // 3 rows that each fail a different validation rule
    // ================================================================

    @Test
    @DisplayName("Bulk upload — invalid rows: 3 bad rows → inserted=0, failed=3, failedRows has reasons")
    void bulkUpload_invalidRows_reportsAllFailures() throws Exception {
        String futureDate = LocalDate.now().plusDays(5).toString();
        String csv = "household_id,reading_date,meter_reading_value,volume_used_liters\n" +
                     // Row 2: non-numeric household_id
                     "NOTANUMBER,2024-01-20,,300.00\n" +
                     // Row 3: future reading date
                     SEEDED_HOUSEHOLD_ID + "," + futureDate + ",,200.00\n" +
                     // Row 4: negative volume
                     SEEDED_HOUSEHOLD_ID + ",2024-01-21,,-50.00\n";

        MockMultipartFile file = csvFile(csv);

        mockMvc.perform(multipart("/api/usage-logs/bulk-upload")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsProcessed").value(3))
                .andExpect(jsonPath("$.rowsInserted").value(0))
                .andExpect(jsonPath("$.rowsSkipped").value(0))
                .andExpect(jsonPath("$.rowsFailed").value(3))
                // failedRows list has 3 entries with non-empty reasons
                .andExpect(jsonPath("$.failedRows", hasSize(3)))
                .andExpect(jsonPath("$.failedRows[0].rowNumber").value(2))
                .andExpect(jsonPath("$.failedRows[0].reason", containsString("household_id")))
                .andExpect(jsonPath("$.failedRows[1].rowNumber").value(3))
                .andExpect(jsonPath("$.failedRows[1].reason", containsString("future")))
                .andExpect(jsonPath("$.failedRows[2].rowNumber").value(4))
                .andExpect(jsonPath("$.failedRows[2].reason", containsString("volume_used_liters")));
    }

    // ================================================================
    // Scenario 3b — MIXED BATCH (some valid, some invalid, some duplicate)
    // ================================================================

    @Test
    @DisplayName("Bulk upload — mixed batch: 1 valid + 1 duplicate + 1 invalid → correct counters")
    void bulkUpload_mixedBatch_correctSummary() throws Exception {
        // Pre-seed one entry so DATE_1 is already in the DB
        String seedCsv = "household_id,reading_date,meter_reading_value,volume_used_liters\n" +
                         SEEDED_HOUSEHOLD_ID + "," + DATE_1 + ",,200.00\n";
        mockMvc.perform(multipart("/api/usage-logs/bulk-upload")
                        .file(csvFile(seedCsv))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Mixed upload:
        String mixedCsv = "household_id,reading_date,meter_reading_value,volume_used_liters\n" +
                          // Row 2: duplicate (DATE_1 just inserted above)
                          SEEDED_HOUSEHOLD_ID + "," + DATE_1 + ",,200.00\n" +
                          // Row 3: valid new row
                          SEEDED_HOUSEHOLD_ID + "," + DATE_2 + ",,400.00\n" +
                          // Row 4: invalid (bad household_id)
                          "BADID,2024-01-15,,100.00\n";

        mockMvc.perform(multipart("/api/usage-logs/bulk-upload")
                        .file(csvFile(mixedCsv))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsProcessed").value(3))
                .andExpect(jsonPath("$.rowsInserted").value(1))
                .andExpect(jsonPath("$.rowsSkipped").value(1))
                .andExpect(jsonPath("$.rowsFailed").value(1))
                .andExpect(jsonPath("$.failedRows", hasSize(1)));
    }

    // ================================================================
    // Bulk upload — auth enforcement
    // ================================================================

    @Test
    @DisplayName("POST /api/usage-logs/bulk-upload — unauthenticated returns 401 or 403")
    void bulkUpload_unauthenticated_returns401or403() throws Exception {
        String csv = "household_id,reading_date,meter_reading_value,volume_used_liters\n" +
                     SEEDED_HOUSEHOLD_ID + ",2024-01-15,,300.0\n";

        mockMvc.perform(multipart("/api/usage-logs/bulk-upload").file(csvFile(csv)))
                .andExpect(status().is(anyOf(equalTo(401), equalTo(403))));
    }

    @Test
    @DisplayName("POST /api/usage-logs/bulk-upload — RESIDENT token returns 403")
    void bulkUpload_residentRole_returns403() throws Exception {
        String csv = "household_id,reading_date,meter_reading_value,volume_used_liters\n" +
                     SEEDED_HOUSEHOLD_ID + ",2024-01-16,,300.0\n";

        mockMvc.perform(multipart("/api/usage-logs/bulk-upload")
                        .file(csvFile(csv))
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/usage-logs/bulk-upload — empty file returns 400")
    void bulkUpload_emptyFile_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/api/usage-logs/bulk-upload")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    // ================================================================
    // Helpers
    // ================================================================

    private UsageLogRequest manualLogRequest(Long householdId, LocalDate date, BigDecimal volume) {
        UsageLogRequest req = new UsageLogRequest();
        req.setHouseholdId(householdId);
        req.setReadingDate(date);
        req.setVolumeUsedLiters(volume);
        req.setSource(UsageSource.MANUAL);
        return req;
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile(
                "file", "usage.csv", "text/csv", content.getBytes());
    }
}
