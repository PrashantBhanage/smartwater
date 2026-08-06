package com.aquatrack.smartwaterbilling.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "billing_cycle_invoices",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_billing_cycle_invoice",
                columnNames = {"household_id", "billing_cycle_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"household", "billingCycle"})
public class BillingCycleInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    @NotNull(message = "household is required")
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "billing_cycle_id", nullable = false)
    @NotNull(message = "billingCycle is required")
    private BillingCycle billingCycle;

    @Column(name = "base_charge", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    @DecimalMin(value = "0.0", inclusive = true, message = "baseCharge must be >= 0")
    private BigDecimal baseCharge = BigDecimal.ZERO;

    @Column(name = "shared_cost_allocation", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    @DecimalMin(value = "0.0", inclusive = true, message = "sharedCostAllocation must be >= 0")
    private BigDecimal sharedCostAllocation = BigDecimal.ZERO;

    @Column(name = "total_charge", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    @DecimalMin(value = "0.0", inclusive = true, message = "totalCharge must be >= 0")
    private BigDecimal totalCharge = BigDecimal.ZERO;

    @Column(name = "paid_status", nullable = false, length = 20)
    @Builder.Default
    @NotBlank(message = "paidStatus is required")
    private String paidStatus = "UNPAID";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        if (this.paidStatus == null) {
            this.paidStatus = "UNPAID";
        }
        recomputeTotal();
    }

    public void recomputeTotal() {
        BigDecimal base = baseCharge != null ? baseCharge : BigDecimal.ZERO;
        BigDecimal shared = sharedCostAllocation != null ? sharedCostAllocation : BigDecimal.ZERO;
        this.totalCharge = base.add(shared);
    }
}
