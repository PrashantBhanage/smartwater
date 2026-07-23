package com.aquatrack.smartwaterbilling.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an apartment complex managed within the SmartWater system.
 */
@Entity
@Table(name = "apartments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"households", "billingCycles", "tariffPlans"})
public class Apartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String address;

    @Column(nullable = false)
    private Integer totalHouseholds;

    @Column(nullable = false)
    private String adminContact;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ----------------------------------------------------------------
    // Relationships
    // ----------------------------------------------------------------

    @OneToMany(mappedBy = "apartment", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Household> households = new ArrayList<>();

    @OneToMany(mappedBy = "apartment", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BillingCycle> billingCycles = new ArrayList<>();

    @OneToMany(mappedBy = "apartment", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TariffPlan> tariffPlans = new ArrayList<>();

    // ----------------------------------------------------------------
    // Lifecycle hooks
    // ----------------------------------------------------------------

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
