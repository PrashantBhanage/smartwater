package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.entity.TariffPlan;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure tariff calculation: converts household metered usage (litres) into a
 * two-tier charge using an apartment's {@link TariffPlan}.
 *
 * <pre>
 * usageKl = volumeLiters / 1000
 * if usageKl ≤ tier1LimitKl:
 *     charge = usageKl × tier1Rate
 * else:
 *     charge = tier1LimitKl × tier1Rate
 *            + (usageKl − tier1LimitKl) × tier2Rate
 * </pre>
 *
 * Amounts are rounded to 2 decimal places (HALF_UP).
 */
@Service
public class TariffBillingEngine {

    private static final BigDecimal LITERS_PER_KL = BigDecimal.valueOf(1000);
    private static final int MONEY_SCALE = 2;

    /**
     * Calculates the metered base charge for a household.
     *
     * @param volumeLiters total metered usage in litres for the billing period
     * @param plan         active tariff plan (must not be null)
     * @return charge rounded to 2 decimal places; {@code 0.00} when volume is null/≤0
     */
    public BigDecimal calculateCharge(BigDecimal volumeLiters, TariffPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Tariff plan is required");
        }
        if (volumeLiters == null || volumeLiters.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal usageKl = volumeLiters.divide(LITERS_PER_KL, 6, RoundingMode.HALF_UP);
        BigDecimal tier1Limit = plan.getTier1LimitKl();
        BigDecimal tier1Rate = plan.getTier1Rate();
        BigDecimal tier2Rate = plan.getTier2Rate();

        BigDecimal charge;
        if (usageKl.compareTo(tier1Limit) <= 0) {
            // Entire volume at tier-1 (includes exact boundary)
            charge = usageKl.multiply(tier1Rate);
        } else {
            BigDecimal tier1Portion = tier1Limit.multiply(tier1Rate);
            BigDecimal excessKl = usageKl.subtract(tier1Limit);
            BigDecimal tier2Portion = excessKl.multiply(tier2Rate);
            charge = tier1Portion.add(tier2Portion);
        }

        return charge.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
