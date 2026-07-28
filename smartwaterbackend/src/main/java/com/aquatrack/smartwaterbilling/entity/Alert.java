package com.aquatrack.smartwaterbilling.entity;

import com.aquatrack.smartwaterbilling.entity.enums.AlertType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * In-app alert record for threshold breaches and suspected leaks.
 */
@Entity
@Table(name = "alerts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"household"})
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AlertType alertType;

    @Column(nullable = false)
    private String message;

    @Column(precision = 10, scale = 2)
    private BigDecimal usageLiters;

    private LocalDate readingDate;

    @Column(nullable = false)
    @Builder.Default
    private Boolean acknowledged = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.acknowledged == null) this.acknowledged = false;
    }
}
