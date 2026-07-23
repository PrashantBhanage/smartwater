package com.aquatrack.smartwaterbilling.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an individual household (flat/unit) within an apartment complex.
 * The {@code dailyThresholdLiters} field drives the GREEN/YELLOW/RED colour-coding
 * for water usage logs linked to this household.
 */
@Entity
@Table(name = "households",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_apartment_flat",
                columnNames = {"apartment_id", "flat_number"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"apartment", "usageLogs", "users"})
public class Household {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 50)
    private String flatNumber;

    @Column(precision = 10, scale = 2)
    private BigDecimal areaSqft;

    @Column(nullable = false)
    private Integer occupancyCount;

    @Column(nullable = false)
    private Boolean hasMeter;

    /**
     * Per-household daily threshold in litres.
     * Usage at or below this → GREEN.
     * Usage above this but below 1.5× → YELLOW.
     * Usage at or above 1.5× → RED.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal dailyThresholdLiters = BigDecimal.valueOf(500.00);

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ----------------------------------------------------------------
    // Relationships
    // ----------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "apartment_id", nullable = false)
    private Apartment apartment;

    @OneToMany(mappedBy = "household", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<WaterUsageLog> usageLogs = new ArrayList<>();

    @OneToMany(mappedBy = "household", fetch = FetchType.LAZY)
    @Builder.Default
    private List<User> users = new ArrayList<>();

    // ----------------------------------------------------------------
    // Lifecycle hooks
    // ----------------------------------------------------------------

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.occupancyCount == null) this.occupancyCount = 1;
        if (this.hasMeter == null) this.hasMeter = false;
        if (this.dailyThresholdLiters == null) this.dailyThresholdLiters = BigDecimal.valueOf(500.00);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
