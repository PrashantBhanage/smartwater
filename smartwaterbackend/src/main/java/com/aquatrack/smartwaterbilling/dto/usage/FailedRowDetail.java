package com.aquatrack.smartwaterbilling.dto.usage;

import lombok.Builder;
import lombok.Data;

/**
 * Details of a single CSV row that failed validation during bulk upload.
 */
@Data
@Builder
public class FailedRowDetail {

    /** 1-indexed row number in the uploaded CSV (excluding header). */
    private int rowNumber;

    /** Raw row data as it appeared in the CSV. */
    private String rawData;

    /** Human-readable reason why this row was rejected. */
    private String reason;
}
