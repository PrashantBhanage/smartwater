package com.aquatrack.smartwaterbilling.entity;

import com.aquatrack.smartwaterbilling.entity.enums.BillingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a billing period (cycle) for an apartment complex.
 * Multiple billing cycles can exist per apartment (one per month/quarter, etc.)
 */
@Entity
@Table(name = "billing_cycles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"apartment"})
public class BillingCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private LocalDate cycleStartDate;

    @Column(nullable = false)
    private LocalDate cycleEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BillingStatus status = BillingStatus.OPEN;

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

    // ----------------------------------------------------------------
    // Lifecycle hooks
    // ----------------------------------------------------------------

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = BillingStatus.OPEN;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
