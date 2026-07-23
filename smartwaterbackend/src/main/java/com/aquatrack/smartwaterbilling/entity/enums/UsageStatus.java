package com.aquatrack.smartwaterbilling.entity.enums;

/**
 * Colour-coded usage status computed at log-creation time and stored
 * permanently so historical data remains consistent even if the threshold changes.
 *
 * <p>Thresholds (based on household.daily_threshold_liters = T):
 * <ul>
 *   <li>GREEN  : volume_used_liters &le; T</li>
 *   <li>YELLOW : T &lt; volume_used_liters &lt; 1.5 &times; T</li>
 *   <li>RED    : volume_used_liters &ge; 1.5 &times; T</li>
 * </ul>
 */
public enum UsageStatus {
    /** Normal consumption — at or below daily threshold. */
    GREEN,
    /** High usage warning — above threshold but below 1.5× threshold. */
    YELLOW,
    /** Critical / possible leak — at or above 1.5× threshold. */
    RED
}
