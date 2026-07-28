package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.dto.usage.*;
import com.aquatrack.smartwaterbilling.entity.Household;
import com.aquatrack.smartwaterbilling.entity.WaterUsageLog;
import com.aquatrack.smartwaterbilling.entity.enums.UsageSource;
import com.aquatrack.smartwaterbilling.entity.enums.UsageStatus;
import com.aquatrack.smartwaterbilling.exception.DuplicateEntryException;
import com.aquatrack.smartwaterbilling.exception.ResourceNotFoundException;
import com.aquatrack.smartwaterbilling.repository.HouseholdRepository;
import com.aquatrack.smartwaterbilling.repository.WaterUsageLogRepository;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Core service for water usage logging — both manual entry and CSV bulk upload.
 *
 * <h2>Colour-coding logic (stored permanently at creation time)</h2>
 * Given household daily threshold T:
 * <ul>
 *   <li>GREEN  : volume &le; T</li>
 *   <li>YELLOW : T &lt; volume &lt; 1.5 &times; T</li>
 *   <li>RED    : volume &ge; 1.5 &times; T</li>
 * </ul>
 * The status is stored in the DB and never recomputed — historical data
 * remains stable even when the threshold changes.
 *
 * <h2>CSV format (with header row)</h2>
 * {@code household_id,reading_date,meter_reading_value,volume_used_liters}
 */
@Service
@RequiredArgsConstructor
public class UsageLogService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final BigDecimal RED_MULTIPLIER = BigDecimal.valueOf(1.5);

    private final WaterUsageLogRepository usageLogRepository;
    private final HouseholdRepository householdRepository;
    private final AlertService alertService;

    // ----------------------------------------------------------------
    // Manual entry — POST /api/usage-logs
    // ----------------------------------------------------------------

    @Transactional
    public UsageLogResponse createLog(UsageLogRequest request) {
        Household household = householdRepository.findById(request.getHouseholdId())
                .orElseThrow(() -> new ResourceNotFoundException("Household", request.getHouseholdId()));

        if (usageLogRepository.existsByHouseholdIdAndReadingDate(
                request.getHouseholdId(), request.getReadingDate())) {
            throw new DuplicateEntryException(
                    "A usage log for household " + request.getHouseholdId() +
                    " on " + request.getReadingDate() + " already exists");
        }

        UsageStatus status = computeUsageStatus(
                request.getVolumeUsedLiters(), household.getDailyThresholdLiters());

        WaterUsageLog log = WaterUsageLog.builder()
                .household(household)
                .readingDate(request.getReadingDate())
                .meterReadingValue(request.getMeterReadingValue())
                .volumeUsedLiters(request.getVolumeUsedLiters())
                .source(request.getSource())
                .usageStatus(status)
                .build();

        WaterUsageLog saved = usageLogRepository.save(log);
        alertService.checkThreshold(household, request.getVolumeUsedLiters(), request.getReadingDate());
        return toResponse(saved, household);
    }

    // ----------------------------------------------------------------
    // Fetch logs for a household
    // ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<UsageLogResponse> getLogsForHousehold(Long householdId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Household", householdId));
        return usageLogRepository.findAllByHouseholdId(householdId)
                .stream()
                .map(log -> toResponse(log, household))
                .toList();
    }

    // ----------------------------------------------------------------
    // CSV bulk upload — POST /api/usage-logs/bulk-upload
    // ----------------------------------------------------------------

    @Transactional
    public BulkUploadSummary bulkUpload(MultipartFile file) throws IOException, CsvException {
        List<FailedRowDetail> failedRows = new ArrayList<>();
        int rowsInserted = 0;
        int rowsSkipped = 0;
        int rowsFailed = 0;
        int greenCount = 0;
        int yellowCount = 0;
        int redCount = 0;

        List<String[]> rows;
        try (CSVReader reader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            rows = reader.readAll();
        }

        // Skip header row (index 0)
        if (rows.isEmpty()) {
            return BulkUploadSummary.builder()
                    .rowsProcessed(0).rowsInserted(0).rowsSkipped(0).rowsFailed(0)
                    .failedRows(failedRows).build();
        }

        List<String[]> dataRows = rows.subList(1, rows.size());
        int rowsProcessed = dataRows.size();

        for (int i = 0; i < dataRows.size(); i++) {
            String[] cols = dataRows.get(i);
            int rowNum = i + 2; // 1-indexed, header is row 1
            String rawData = String.join(",", cols);

            // -- Parse and validate --
            ParseResult parsed = parseRow(cols, rowNum, rawData, failedRows);
            if (parsed == null) {
                rowsFailed++;
                continue;
            }

            // -- Lookup household --
            Household household;
            try {
                household = householdRepository.findById(parsed.householdId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Household", parsed.householdId));
            } catch (ResourceNotFoundException e) {
                failedRows.add(FailedRowDetail.builder()
                        .rowNumber(rowNum).rawData(rawData)
                        .reason("Household ID " + parsed.householdId + " not found")
                        .build());
                rowsFailed++;
                continue;
            }

            // -- Duplicate check --
            if (usageLogRepository.existsByHouseholdIdAndReadingDate(
                    parsed.householdId, parsed.readingDate)) {
                rowsSkipped++;
                continue;
            }

            // -- Compute colour-coded status --
            UsageStatus status = computeUsageStatus(
                    parsed.volumeUsedLiters, household.getDailyThresholdLiters());

            // -- Persist --
            WaterUsageLog log = WaterUsageLog.builder()
                    .household(household)
                    .readingDate(parsed.readingDate)
                    .meterReadingValue(parsed.meterReadingValue)
                    .volumeUsedLiters(parsed.volumeUsedLiters)
                    .source(UsageSource.CSV_UPLOAD)
                    .usageStatus(status)
                    .build();

            usageLogRepository.save(log);
            rowsInserted++;
            alertService.checkThreshold(household, parsed.volumeUsedLiters, parsed.readingDate);

            switch (status) {
                case GREEN  -> greenCount++;
                case YELLOW -> yellowCount++;
                case RED    -> redCount++;
            }
        }

        return BulkUploadSummary.builder()
                .rowsProcessed(rowsProcessed)
                .rowsInserted(rowsInserted)
                .rowsSkipped(rowsSkipped)
                .rowsFailed(rowsFailed)
                .greenCount(greenCount)
                .yellowCount(yellowCount)
                .redCount(redCount)
                .failedRows(failedRows)
                .build();
    }

    // ----------------------------------------------------------------
    // Colour-coding computation
    // Package-private so unit tests can call it directly.
    // ----------------------------------------------------------------

    /**
     * Determines the usage status for a given volume against the household threshold.
     *
     * <p>Boundary rules (important for tests):
     * <ul>
     *   <li>volume == threshold         → GREEN (inclusive lower bound)</li>
     *   <li>volume == 1.5 × threshold   → RED   (inclusive upper bound starts here)</li>
     * </ul>
     *
     * @param volume    volume consumed in litres
     * @param threshold per-household daily threshold in litres
     * @return GREEN, YELLOW, or RED
     */
    UsageStatus computeUsageStatus(BigDecimal volume, BigDecimal threshold) {
        // GREEN: volume <= threshold
        if (volume.compareTo(threshold) <= 0) {
            return UsageStatus.GREEN;
        }
        // RED threshold = 1.5 * household daily threshold
        BigDecimal redThreshold = threshold.multiply(RED_MULTIPLIER);

        // RED: volume >= 1.5 * threshold
        if (volume.compareTo(redThreshold) >= 0) {
            return UsageStatus.RED;
        }
        // YELLOW: threshold < volume < 1.5 * threshold
        return UsageStatus.YELLOW;
    }

    // ----------------------------------------------------------------
    // CSV row parser
    // ----------------------------------------------------------------

    /** Returns null and adds a FailedRowDetail if parsing fails. */
    private ParseResult parseRow(String[] cols, int rowNum, String rawData,
                                  List<FailedRowDetail> failedRows) {
        // Expected columns: household_id, reading_date, meter_reading_value, volume_used_liters
        if (cols.length < 4) {
            failedRows.add(FailedRowDetail.builder()
                    .rowNumber(rowNum).rawData(rawData)
                    .reason("Expected 4 columns (household_id, reading_date, " +
                            "meter_reading_value, volume_used_liters), got " + cols.length)
                    .build());
            return null;
        }

        long householdId;
        try {
            householdId = Long.parseLong(cols[0].trim());
        } catch (NumberFormatException e) {
            failedRows.add(FailedRowDetail.builder()
                    .rowNumber(rowNum).rawData(rawData)
                    .reason("household_id must be a valid integer, got: '" + cols[0].trim() + "'")
                    .build());
            return null;
        }

        LocalDate readingDate;
        try {
            readingDate = LocalDate.parse(cols[1].trim(), DATE_FMT);
        } catch (DateTimeParseException e) {
            failedRows.add(FailedRowDetail.builder()
                    .rowNumber(rowNum).rawData(rawData)
                    .reason("reading_date must be in yyyy-MM-dd format, got: '" + cols[1].trim() + "'")
                    .build());
            return null;
        }

        if (readingDate.isAfter(LocalDate.now())) {
            failedRows.add(FailedRowDetail.builder()
                    .rowNumber(rowNum).rawData(rawData)
                    .reason("reading_date cannot be in the future: " + readingDate)
                    .build());
            return null;
        }

        BigDecimal meterReadingValue = null;
        String meterStr = cols[2].trim();
        if (!meterStr.isEmpty()) {
            try {
                meterReadingValue = new BigDecimal(meterStr);
                if (meterReadingValue.compareTo(BigDecimal.ZERO) < 0) {
                    throw new NumberFormatException("negative");
                }
            } catch (NumberFormatException e) {
                failedRows.add(FailedRowDetail.builder()
                        .rowNumber(rowNum).rawData(rawData)
                        .reason("meter_reading_value must be a non-negative number, got: '" + meterStr + "'")
                        .build());
                return null;
            }
        }

        BigDecimal volumeUsedLiters;
        try {
            volumeUsedLiters = new BigDecimal(cols[3].trim());
            if (volumeUsedLiters.compareTo(BigDecimal.ZERO) < 0) {
                throw new NumberFormatException("negative");
            }
        } catch (NumberFormatException e) {
            failedRows.add(FailedRowDetail.builder()
                    .rowNumber(rowNum).rawData(rawData)
                    .reason("volume_used_liters must be a non-negative number, got: '" + cols[3].trim() + "'")
                    .build());
            return null;
        }

        return new ParseResult(householdId, readingDate, meterReadingValue, volumeUsedLiters);
    }

    // ----------------------------------------------------------------
    // Mapper
    // ----------------------------------------------------------------

    private UsageLogResponse toResponse(WaterUsageLog log, Household household) {
        return UsageLogResponse.builder()
                .id(log.getId())
                .householdId(household.getId())
                .flatNumber(household.getFlatNumber())
                .apartmentId(household.getApartment().getId())
                .readingDate(log.getReadingDate())
                .meterReadingValue(log.getMeterReadingValue())
                .volumeUsedLiters(log.getVolumeUsedLiters())
                .dailyThresholdLiters(household.getDailyThresholdLiters())
                .source(log.getSource())
                .usageStatus(log.getUsageStatus())
                .createdAt(log.getCreatedAt())
                .build();
    }

    // ----------------------------------------------------------------
    // Internal record for parsed CSV row data
    // ----------------------------------------------------------------

    private record ParseResult(
            long householdId,
            LocalDate readingDate,
            BigDecimal meterReadingValue,
            BigDecimal volumeUsedLiters) {
    }
}
