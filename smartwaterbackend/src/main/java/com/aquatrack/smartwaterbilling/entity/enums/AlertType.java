package com.aquatrack.smartwaterbilling.entity.enums;

/**
 * Types of in-app / emailed usage alerts.
 */
public enum AlertType {
    /** Daily usage crossed the household's configured daily_threshold_liters. */
    THRESHOLD_EXCEEDED,
    /** Usage is more than 2σ above the household's own historical average. */
    LEAK_SUSPECTED
}
