package com.aquatrack.smartwaterbilling.entity;

import com.aquatrack.smartwaterbilling.entity.enums.UsageSource;
import com.aquatrack.smartwaterbilling.entity.enums.UsageStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Records a single water usage reading for a {@link Household} on a specific date.
 * The {@code usageStatus} is computed by {@code UsageLogService} at creation time
 * and stored here permanently — it does NOT change if the threshold is later updated.
 * This preserves historical data integrity.
 *
 * <p>The (household_id, reading_date) pair is unique (enforced by DB constraint
 * {@code uq_usage_log_household_date}) to prevent duplicate entries.
 */
@Entity
@Table(name = "water_usage_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_usage_log_household_date",
                columnNames = {"household_id", "reading_date"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"household"})
public class WaterUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private LocalDate readingDate;

    /** Cumulative meter reading value (optional if no meter installed). */
    @Column(precision = 15, scale = 3)
    private BigDecimal meterReadingValue;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal volumeUsedLiters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UsageSource source;

    /**
     * Colour-coded status computed at creation time from household threshold.
     * Stored to keep historical records stable.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UsageStatus usageStatus;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ----------------------------------------------------------------
    // Relationships
    // ----------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    // ----------------------------------------------------------------
    // Lifecycle hooks
    // ----------------------------------------------------------------

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
