package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.entity.Apartment;
import com.aquatrack.smartwaterbilling.entity.BillingCycle;
import com.aquatrack.smartwaterbilling.entity.Household;
import com.aquatrack.smartwaterbilling.entity.WaterUsageLog;
import com.aquatrack.smartwaterbilling.repository.HouseholdRepository;
import com.aquatrack.smartwaterbilling.repository.WaterUsageLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class CostDistributionService {

    @Autowired(required = false)
    private HouseholdRepository householdRepository;
    @Autowired(required = false)
    private WaterUsageLogRepository usageLogRepository;

    public CostDistributionService() {}

    public CostDistributionService(HouseholdRepository householdRepository, WaterUsageLogRepository usageLogRepository) {
        this.householdRepository = householdRepository;
        this.usageLogRepository = usageLogRepository;
    }


    private static final int MONEY_SCALE = 2;

    /**
     * Legacy distribute method for compatibility.
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
            result.putAll(splitByUsageLegacy(metered, usageByHousehold, sharedCost));
        } else if (metered.isEmpty()) {
            result.putAll(splitByAreaLegacy(unmetered, sharedCost));
        } else {
            BigDecimal totalCount = BigDecimal.valueOf(households.size());
            BigDecimal meteredPool = sharedCost
                    .multiply(BigDecimal.valueOf(metered.size()))
                    .divide(totalCount, MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal unmeteredPool = sharedCost.subtract(meteredPool);

            result.putAll(splitByUsageLegacy(metered, usageByHousehold, meteredPool));
            result.putAll(splitByAreaLegacy(unmetered, unmeteredPool));
        }

        reconcileLegacy(households, result, sharedCost);
        return result;
    }

    private Map<Long, BigDecimal> splitByUsageLegacy(
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
            return equalSplitLegacy(households, pool);
        }

        Map<Long, BigDecimal> shares = new HashMap<>();
        for (Household h : households) {
            BigDecimal share = pool.multiply(usage.get(h.getId()))
                    .divide(totalUsage, MONEY_SCALE, RoundingMode.HALF_UP);
            shares.put(h.getId(), share);
        }
        return shares;
    }

    private Map<Long, BigDecimal> splitByAreaLegacy(List<Household> households, BigDecimal pool) {
        BigDecimal totalArea = BigDecimal.ZERO;
        Map<Long, BigDecimal> areas = new HashMap<>();
        for (Household h : households) {
            BigDecimal area = h.getAreaSqft() != null ? h.getAreaSqft() : BigDecimal.ZERO;
            if (area.compareTo(BigDecimal.ZERO) < 0) area = BigDecimal.ZERO;
            areas.put(h.getId(), area);
            totalArea = totalArea.add(area);
        }

        if (totalArea.compareTo(BigDecimal.ZERO) == 0) {
            return equalSplitLegacy(households, pool);
        }

        Map<Long, BigDecimal> shares = new HashMap<>();
        for (Household h : households) {
            BigDecimal share = pool.multiply(areas.get(h.getId()))
                    .divide(totalArea, MONEY_SCALE, RoundingMode.HALF_UP);
            shares.put(h.getId(), share);
        }
        return shares;
    }

    private Map<Long, BigDecimal> equalSplitLegacy(List<Household> households, BigDecimal pool) {
        int n = households.size();
        BigDecimal each = pool.divide(BigDecimal.valueOf(n), MONEY_SCALE, RoundingMode.HALF_UP);
        Map<Long, BigDecimal> shares = new HashMap<>();
        for (Household h : households) {
            shares.put(h.getId(), each);
        }
        return shares;
    }

    private void reconcileLegacy(List<Household> households, Map<Long, BigDecimal> shares, BigDecimal target) {
        BigDecimal sum = shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal delta = target.subtract(sum);
        if (delta.compareTo(BigDecimal.ZERO) != 0 && !households.isEmpty()) {
            Long firstId = households.get(0).getId();
            shares.put(firstId, shares.get(firstId).add(delta));
        }
    }

    // ----------------------------------------------------------------
    // NEW Milestone 2 distribution logic
    // ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<Household, BigDecimal> distributeApartmentCost(
            Apartment apartment, BillingCycle cycle, BigDecimal totalCost) {

        if (apartment == null || cycle == null) {
            throw new IllegalArgumentException("Apartment and BillingCycle are required");
        }

        List<Household> households = householdRepository.findAllByApartmentId(apartment.getId());
        if (households.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Household, BigDecimal> result = new HashMap<>();
        if (totalCost == null || totalCost.compareTo(BigDecimal.ZERO) <= 0) {
            for (Household h : households) {
                result.put(h, BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            }
            return result;
        }

        // Fetch usage for all households in this cycle
        Map<Household, BigDecimal> usageMap = new HashMap<>();
        BigDecimal totalUsage = BigDecimal.ZERO;

        for (Household h : households) {
            List<WaterUsageLog> logs = usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(
                    h.getId(), cycle.getCycleStartDate(), cycle.getCycleEndDate());
            BigDecimal usage = logs.stream()
                    .map(WaterUsageLog::getVolumeUsedLiters)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            usageMap.put(h, usage);
            totalUsage = totalUsage.add(usage);
        }

        if (totalUsage.compareTo(BigDecimal.ZERO) > 0) {
            // Proportional split by usage
            for (Household h : households) {
                BigDecimal usage = usageMap.get(h);
                BigDecimal share = totalCost.multiply(usage)
                        .divide(totalUsage, MONEY_SCALE, RoundingMode.HALF_UP);
                result.put(h, share);
            }
        } else {
            // Fallback: split by flat_area
            BigDecimal totalArea = BigDecimal.ZERO;
            Map<Household, BigDecimal> areaMap = new HashMap<>();
            for (Household h : households) {
                BigDecimal area = h.getAreaSqft() != null ? h.getAreaSqft() : BigDecimal.ZERO;
                if (area.compareTo(BigDecimal.ZERO) < 0) {
                    area = BigDecimal.ZERO;
                }
                areaMap.put(h, area);
                totalArea = totalArea.add(area);
            }

            if (totalArea.compareTo(BigDecimal.ZERO) > 0) {
                for (Household h : households) {
                    BigDecimal area = areaMap.get(h);
                    BigDecimal share = totalCost.multiply(area)
                            .divide(totalArea, MONEY_SCALE, RoundingMode.HALF_UP);
                    result.put(h, share);
                }
            } else {
                // Split evenly if no area or usage info
                BigDecimal count = BigDecimal.valueOf(households.size());
                BigDecimal evenShare = totalCost.divide(count, MONEY_SCALE, RoundingMode.HALF_UP);
                for (Household h : households) {
                    result.put(h, evenShare);
                }
            }
        }

        // Reconcile rounding issues by adjusting the first household's share
        BigDecimal sum = result.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal delta = totalCost.subtract(sum);
        if (delta.compareTo(BigDecimal.ZERO) != 0 && !households.isEmpty()) {
            Household first = households.get(0);
            result.put(first, result.get(first).add(delta));
        }

        return result;
    }
}
