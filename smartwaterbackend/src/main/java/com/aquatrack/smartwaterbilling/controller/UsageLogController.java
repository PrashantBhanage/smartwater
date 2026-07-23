package com.aquatrack.smartwaterbilling.controller;

import com.aquatrack.smartwaterbilling.dto.usage.BulkUploadSummary;
import com.aquatrack.smartwaterbilling.dto.usage.UsageLogRequest;
import com.aquatrack.smartwaterbilling.dto.usage.UsageLogResponse;
import com.aquatrack.smartwaterbilling.service.UsageLogService;
import com.opencsv.exceptions.CsvException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * REST controller for water usage logging.
 *
 * <ul>
 *   <li>{@code POST /api/usage-logs}             — manual single entry (ADMIN or RESIDENT)</li>
 *   <li>{@code GET  /api/usage-logs}             — list logs for a household</li>
 *   <li>{@code POST /api/usage-logs/bulk-upload} — CSV bulk import (ADMIN only)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/usage-logs")
@RequiredArgsConstructor
public class UsageLogController {

    private final UsageLogService usageLogService;

    /**
     * Manual single-entry water usage log.
     * Computes and stores the GREEN/YELLOW/RED status at creation time.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UsageLogResponse> create(@Valid @RequestBody UsageLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usageLogService.createLog(request));
    }

    /**
     * Retrieve all usage logs for a specific household.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UsageLogResponse>> getByHousehold(
            @RequestParam Long householdId) {
        return ResponseEntity.ok(usageLogService.getLogsForHousehold(householdId));
    }

    /**
     * Bulk CSV upload endpoint.
     * Accepts a multipart/form-data file with the following CSV format (header required):
     * {@code household_id,reading_date,meter_reading_value,volume_used_liters}
     *
     * <p>Returns a summary of: rows processed, inserted, skipped (duplicates),
     * failed (validation errors), and GREEN/YELLOW/RED counts.
     */
    @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkUploadSummary> bulkUpload(
            @RequestParam("file") MultipartFile file) throws IOException, CsvException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String contentType = file.getContentType();
        if (contentType != null &&
                !contentType.equals("text/csv") &&
                !contentType.equals("application/vnd.ms-excel") &&
                !contentType.equals("text/plain")) {
            // Accept common CSV MIME types; reject obviously wrong types
        }

        return ResponseEntity.ok(usageLogService.bulkUpload(file));
    }
}
