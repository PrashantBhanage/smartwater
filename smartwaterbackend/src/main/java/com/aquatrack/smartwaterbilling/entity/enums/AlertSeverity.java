package com.aquatrack.smartwaterbilling.entity.enums;

/**
 * Severity level for an {@link com.aquatrack.smartwaterbilling.entity.Alert}.
 *
 * <ul>
 *   <li>{@code LOW} — informational (reserved, not currently assigned)</li>
 *   <li>{@code MEDIUM} — default; assigned to {@code THRESHOLD_EXCEEDED}</li>
 *   <li>{@code HIGH} — assigned to {@code LEAK_SUSPECTED}</li>
 *   <li>{@code CRITICAL} — reserved for future escalation</li>
 * </ul>
 */
public enum AlertSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}