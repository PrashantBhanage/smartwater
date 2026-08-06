package com.aquatrack.smartwaterbilling.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bulk_water_purchases")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"apartment"})
public class BulkWaterPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "apartment_id", nullable = false)
    @NotNull(message = "apartment is required")
    private Apartment apartment;

    @Column(name = "purchase_date", nullable = false)
    @NotNull(message = "purchaseDate is required")
    private LocalDate purchaseDate;

    @Column(name = "volume_liters", nullable = false, precision = 12, scale = 2)
    @NotNull(message = "volumeLiters is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "volumeLiters must be >= 0")
    @PositiveOrZero(message = "volumeLiters must be >= 0")
    private BigDecimal volumeLiters;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 4)
    @NotNull(message = "unitCost is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "unitCost must be >= 0")
    @PositiveOrZero(message = "unitCost must be >= 0")
    private BigDecimal unitCost;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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

    public BigDecimal totalCost() {
        if (volumeLiters == null || unitCost == null) {
            return BigDecimal.ZERO;
        }
        // Unit cost is price per liter? Let's check: total cost = volume_liters * unit_cost
        return volumeLiters.multiply(unitCost);
    }
}
