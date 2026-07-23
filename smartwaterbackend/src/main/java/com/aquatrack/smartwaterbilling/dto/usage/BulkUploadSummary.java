package com.aquatrack.smartwaterbilling.dto.usage;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Summary response returned after a CSV bulk upload.
 * Includes per-status counts so the frontend can display a breakdown.
 */
@Data
@Builder
public class BulkUploadSummary {

    /** Total data rows processed (header excluded). */
    private int rowsProcessed;

    /** Rows successfully validated and inserted. */
    private int rowsInserted;

    /**
     * Rows skipped because a log for the same (household_id, reading_date)
     * already exists in the database.
     */
    private int rowsSkipped;

    /** Rows that failed parsing or business-rule validation. */
    private int rowsFailed;

    // ---- Colour-code breakdown of successfully inserted rows ----
    private int greenCount;
    private int yellowCount;
    private int redCount;

    /** Detailed list of every row that failed, with the reason. */
    private List<FailedRowDetail> failedRows;
}
