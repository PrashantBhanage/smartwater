package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.entity.BillingCycle;
import com.aquatrack.smartwaterbilling.entity.Household;
import com.aquatrack.smartwaterbilling.entity.TariffPlan;
import com.aquatrack.smartwaterbilling.entity.WaterUsageLog;
import com.aquatrack.smartwaterbilling.repository.WaterUsageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TariffCalculationService {

    private final WaterUsageLogRepository usageLogRepository;
    private final TariffPlanService tariffPlanService;

    private static final BigDecimal LITERS_PER_KL = BigDecimal.valueOf(1000);
    private static final int MONEY_SCALE = 2;

    @Transactional(readOnly = true)
    public BigDecimal calculateHouseholdBill(Household household, BillingCycle cycle) {
        if (household == null || cycle == null) {
            throw new IllegalArgumentException("Household and BillingCycle are required");
        }

        // Get total consumption in liters for the household in this cycle
        List<WaterUsageLog> logs = usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(
                household.getId(), cycle.getCycleStartDate(), cycle.getCycleEndDate());
        
        BigDecimal volumeLiters = logs.stream()
                .map(WaterUsageLog::getVolumeUsedLiters)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (volumeLiters.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        // Get active tariff plan
        TariffPlan plan = tariffPlanService.requireActivePlan(
                household.getApartment().getId(), cycle.getCycleEndDate());

        return calculateBillWithPlan(volumeLiters, plan);
    }

    public BigDecimal calculateBillWithPlan(BigDecimal volumeLiters, TariffPlan plan) {
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
