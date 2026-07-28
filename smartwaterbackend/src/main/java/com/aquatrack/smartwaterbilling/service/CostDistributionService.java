package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.entity.Household;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Apportions apartment-level shared water-purchase cost across households.
 *
 * <h2>Rules</h2>
 * <ul>
 *   <li><strong>All metered</strong> — split proportional to each household's
 *       metered volume (litres). Zero total volume → equal split.</li>
 *   <li><strong>All unmetered</strong> — split proportional to {@code areaSqft}.
 *       Missing/zero total area → equal split.</li>
 *   <li><strong>Mixed</strong> — shared cost is first split between the metered
 *       and unmetered groups by headcount, then each group applies its own
 *       rule above.</li>
 * </ul>
 *
 * Returned amounts are scaled to 2 decimal places (HALF_UP). Any residual
 * rounding remainder is added to the first household so totals match exactly.
 */
@Service
public class CostDistributionService {

    private static final int MONEY_SCALE = 2;

    /**
     * @param households      all households in the apartment
     * @param usageByHousehold householdId → total metered litres in the cycle
     *                         (may omit unmetered households; treated as 0)
     * @param sharedCost      total purchase cost to apportion (≥ 0)
     * @return householdId → allocated amount (every household appears; sum equals sharedCost)
     */
    public Map<Long, BigDecimal> distribute(
            List<Household> households,
            Map<Long, BigDecimal> usageByHousehold,
            BigDecimal sharedCost) {

        if (households == null || households.isEmpty()) {
            return Collections.emptyMap();
        }
        if (sharedCost == null || sharedCost.compareTo(BigDecimal.ZERO) <= 0) {
            Map<Long, BigDecimal> zeros = new HashMap<>();
            for (Household h : households) {
                zeros.put(h.getId(), BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            }
            return zeros;
        }

        List<Household> metered = households.stream()
                .filter(h -> Boolean.TRUE.equals(h.getHasMeter()))
                .toList();
        List<Household> unmetered = households.stream()
                .filter(h -> !Boolean.TRUE.equals(h.getHasMeter()))
                .toList();

        Map<Long, BigDecimal> result = new HashMap<>();

        if (unmetered.isEmpty()) {
            // All metered
            result.putAll(splitByUsage(metered, usageByHousehold, sharedCost));
        } else if (metered.isEmpty()) {
            // All unmetered
            result.putAll(splitByArea(unmetered, sharedCost));
        } else {
            // Mixed: headcount-weighted pools
            BigDecimal totalCount = BigDecimal.valueOf(households.size());
            BigDecimal meteredPool = sharedCost
                    .multiply(BigDecimal.valueOf(metered.size()))
                    .divide(totalCount, MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal unmeteredPool = sharedCost.subtract(meteredPool);

            result.putAll(splitByUsage(metered, usageByHousehold, meteredPool));
            result.putAll(splitByArea(unmetered, unmeteredPool));
        }

        // Absorb rounding residual into the first household so sum == sharedCost
        reconcile(households, result, sharedCost);
        return result;
    }

    // ----------------------------------------------------------------
    // Split helpers
    // ----------------------------------------------------------------

    Map<Long, BigDecimal> splitByUsage(
            List<Household> households,
            Map<Long, BigDecimal> usageByHousehold,
            BigDecimal pool) {

        Map<Long, BigDecimal> usage = new HashMap<>();
        BigDecimal totalUsage = BigDecimal.ZERO;
        for (Household h : households) {
            BigDecimal u = usageByHousehold.getOrDefault(h.getId(), BigDecimal.ZERO);
            if (u.compareTo(BigDecimal.ZERO) < 0) u = BigDecimal.ZERO;
            usage.put(h.getId(), u);
            totalUsage = totalUsage.add(u);
        }

        if (totalUsage.compareTo(BigDecimal.ZERO) == 0) {
            return equalSplit(households, pool);
        }

        Map<Long, BigDecimal> shares = new HashMap<>();
        for (Household h : households) {
            BigDecimal share = pool.multiply(usage.get(h.getId()))
                    .divide(totalUsage, MONEY_SCALE, RoundingMode.HALF_UP);
            shares.put(h.getId(), share);
        }
        return shares;
    }

    Map<Long, BigDecimal> splitByArea(List<Household> households, BigDecimal pool) {
        BigDecimal totalArea = BigDecimal.ZERO;
        Map<Long, BigDecimal> areas = new HashMap<>();
        for (Household h : households) {
            BigDecimal area = h.getAreaSqft() != null ? h.getAreaSqft() : BigDecimal.ZERO;
            if (area.compareTo(BigDecimal.ZERO) < 0) area = BigDecimal.ZERO;
            areas.put(h.getId(), area);
            totalArea = totalArea.add(area);
        }

        if (totalArea.compareTo(BigDecimal.ZERO) == 0) {
            return equalSplit(households, pool);
        }

        Map<Long, BigDecimal> shares = new HashMap<>();
        for (Household h : households) {
            BigDecimal share = pool.multiply(areas.get(h.getId()))
                    .divide(totalArea, MONEY_SCALE, RoundingMode.HALF_UP);
            shares.put(h.getId(), share);
        }
        return shares;
    }

    private Map<Long, BigDecimal> equalSplit(List<Household> households, BigDecimal pool) {
        int n = households.size();
        BigDecimal each = pool.divide(BigDecimal.valueOf(n), MONEY_SCALE, RoundingMode.HALF_UP);
        Map<Long, BigDecimal> shares = new HashMap<>();
        for (Household h : households) {
            shares.put(h.getId(), each);
        }
        return shares;
    }

    private void reconcile(List<Household> households, Map<Long, BigDecimal> shares, BigDecimal target) {
        BigDecimal sum = shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal delta = target.subtract(sum);
        if (delta.compareTo(BigDecimal.ZERO) != 0 && !households.isEmpty()) {
            Long firstId = households.get(0).getId();
            shares.put(firstId, shares.get(firstId).add(delta));
        }
    }
}
