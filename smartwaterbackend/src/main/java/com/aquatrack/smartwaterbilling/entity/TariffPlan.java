package com.aquatrack.smartwaterbilling.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Defines the water tariff tiers for an apartment complex.
 * Multiple plans can exist per apartment; {@code effectiveFromDate} determines
 * which plan is active for a given billing cycle.
 */
@Entity
@Table(name = "tariff_plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"apartment"})
public class TariffPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** Maximum volume (in kilolitres) eligible for the Tier 1 rate. */
    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal tier1LimitKl;

    /** Price per kilolitre for consumption up to tier1LimitKl. */
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal tier1Rate;

    /** Price per kilolitre for consumption exceeding tier1LimitKl. */
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal tier2Rate;

    /** Date from which this plan becomes effective. */
    @Column(nullable = false)
    private LocalDate effectiveFromDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ----------------------------------------------------------------
    // Relationships
    // ----------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "apartment_id", nullable = false)
    private Apartment apartment;

    // ----------------------------------------------------------------
    // Lifecycle hooks
    // ----------------------------------------------------------------

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
