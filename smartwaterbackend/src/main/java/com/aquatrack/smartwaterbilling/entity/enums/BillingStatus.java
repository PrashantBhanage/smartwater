package com.aquatrack.smartwaterbilling.entity.enums;

/**
 * Lifecycle status of a billing cycle.
 * OPEN      - active, usage logs can be linked.
 * FINALIZED - billing calculated and locked.
 * ARCHIVED  - historical record, no further changes.
 */
public enum BillingStatus {
    OPEN,
    FINALIZED,
    ARCHIVED
}
