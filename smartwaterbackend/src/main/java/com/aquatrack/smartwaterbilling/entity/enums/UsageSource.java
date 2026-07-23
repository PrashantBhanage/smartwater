package com.aquatrack.smartwaterbilling.entity.enums;

/**
 * Source of a water usage log entry.
 * MANUAL - entered by a user via the API.
 * CSV_UPLOAD - imported via bulk CSV upload.
 */
public enum UsageSource {
    MANUAL,
    CSV_UPLOAD
}
