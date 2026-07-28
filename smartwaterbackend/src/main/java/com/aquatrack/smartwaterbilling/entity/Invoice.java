package com.aquatrack.smartwaterbilling.entity;

import com.aquatrack.smartwaterbilling.entity.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Per-household invoice generated when a billing cycle is finalized.
 * {@code totalAmount = baseCharge + sharedAllocation + adjustments}.
 */
@Entity
@Table(name = "invoices",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_invoice_household_cycle",
                columnNames = {"household_id", "cycle_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"household", "billingCycle"})
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** Metered tariff charge for the household's cycle usage. */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal baseCharge = BigDecimal.ZERO;

    /** Share of apartment-level purchase costs allocated to this household. */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal sharedAllocation = BigDecimal.ZERO;

    /** Manual credits / debits applied at finalize time (may be negative). */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal adjustments = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false)
    private BillingCycle billingCycle;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = InvoiceStatus.ISSUED;
        recomputeTotal();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        recomputeTotal();
    }

    public void recomputeTotal() {
        BigDecimal base = baseCharge != null ? baseCharge : BigDecimal.ZERO;
        BigDecimal shared = sharedAllocation != null ? sharedAllocation : BigDecimal.ZERO;
        BigDecimal adj = adjustments != null ? adjustments : BigDecimal.ZERO;
        this.totalAmount = base.add(shared).add(adj);
    }
}
