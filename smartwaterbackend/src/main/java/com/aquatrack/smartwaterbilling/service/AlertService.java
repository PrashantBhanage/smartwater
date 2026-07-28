package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.dto.alert.AlertResponse;
import com.aquatrack.smartwaterbilling.entity.Alert;
import com.aquatrack.smartwaterbilling.entity.Household;
import com.aquatrack.smartwaterbilling.entity.User;
import com.aquatrack.smartwaterbilling.entity.WaterUsageLog;
import com.aquatrack.smartwaterbilling.entity.enums.AlertType;
import com.aquatrack.smartwaterbilling.exception.ResourceNotFoundException;
import com.aquatrack.smartwaterbilling.repository.AlertRepository;
import com.aquatrack.smartwaterbilling.repository.HouseholdRepository;
import com.aquatrack.smartwaterbilling.repository.UserRepository;
import com.aquatrack.smartwaterbilling.repository.WaterUsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Threshold and leak-detection alert engine.
 *
 * <ul>
 *   <li>{@link #checkThreshold} — called when a usage log is created; fires if
 *       volume exceeds the household's {@code dailyThresholdLiters}.</li>
 *   <li>{@link #scanForLeaks} — {@code @Scheduled} job that flags households
 *       whose latest reading is &gt; mean + 2σ of their own history.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    /** Minimum historical readings required before leak detection runs. */
    static final int MIN_HISTORY_SAMPLES = 7;

    private final AlertRepository alertRepository;
    private final HouseholdRepository householdRepository;
    private final WaterUsageLogRepository usageLogRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Value("${app.alerts.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    // ----------------------------------------------------------------
    // Threshold check (on usage log create)
    // ----------------------------------------------------------------

    @Transactional
    public void checkThreshold(Household household, BigDecimal volumeLiters, LocalDate readingDate) {
        if (household.getDailyThresholdLiters() == null || volumeLiters == null) {
            return;
        }
        if (volumeLiters.compareTo(household.getDailyThresholdLiters()) <= 0) {
            return;
        }
        // Deduplicate: one threshold alert per household per reading date
        if (alertRepository.existsByHouseholdIdAndAlertTypeAndReadingDate(
                household.getId(), AlertType.THRESHOLD_EXCEEDED, readingDate)) {
            return;
        }

        String message = String.format(
                "Household %s exceeded daily threshold: %.2f L > %.2f L on %s",
                household.getFlatNumber(),
                volumeLiters,
                household.getDailyThresholdLiters(),
                readingDate);

        persistAndNotify(household, AlertType.THRESHOLD_EXCEEDED, message, volumeLiters, readingDate);
    }

    // ----------------------------------------------------------------
    // Leak scan (scheduled)
    // ----------------------------------------------------------------

    /**
     * Runs daily at 02:00. Disabled in tests via {@code app.alerts.scheduler.enabled=false}.
     */
    @Scheduled(cron = "${app.alerts.scheduler.cron:0 0 2 * * *}")
    @Transactional
    public void scanForLeaks() {
        if (!schedulerEnabled) {
            log.debug("Leak-detection scheduler disabled — skipping");
            return;
        }
        log.info("Starting leak-detection scan");
        int flagged = 0;
        for (Household household : householdRepository.findAll()) {
            if (evaluateLeakForHousehold(household)) {
                flagged++;
            }
        }
        log.info("Leak-detection scan complete — {} household(s) flagged", flagged);
    }

    /**
     * Evaluates a single household for leak suspicion. Package-visible for tests.
     *
     * @return true if a new LEAK_SUSPECTED alert was created
     */
    @Transactional
    public boolean evaluateLeakForHousehold(Household household) {
        List<WaterUsageLog> logs = usageLogRepository.findAllByHouseholdId(household.getId());
        if (logs.size() < MIN_HISTORY_SAMPLES + 1) {
            return false; // need history + one candidate reading
        }

        logs = new ArrayList<>(logs);
        logs.sort(Comparator.comparing(WaterUsageLog::getReadingDate));

        WaterUsageLog latest = logs.get(logs.size() - 1);
        List<BigDecimal> history = logs.subList(0, logs.size() - 1).stream()
                .map(WaterUsageLog::getVolumeUsedLiters)
                .toList();

        if (!isOutlier(latest.getVolumeUsedLiters(), history)) {
            return false;
        }

        if (alertRepository.existsByHouseholdIdAndAlertTypeAndReadingDate(
                household.getId(), AlertType.LEAK_SUSPECTED, latest.getReadingDate())) {
            return false;
        }

        String message = String.format(
                "Potential leak at household %s: latest reading %.2f L on %s is > 2σ above historical average",
                household.getFlatNumber(),
                latest.getVolumeUsedLiters(),
                latest.getReadingDate());

        persistAndNotify(household, AlertType.LEAK_SUSPECTED, message,
                latest.getVolumeUsedLiters(), latest.getReadingDate());
        return true;
    }

    // ----------------------------------------------------------------
    // Outlier math (pure — unit-tested)
    // ----------------------------------------------------------------

    /**
     * Returns true when {@code value} is strictly greater than mean + 2×stddev
     * of {@code history}. Requires at least {@link #MIN_HISTORY_SAMPLES} points.
     * When stddev is 0, any value strictly above the constant mean is an outlier.
     */
    public boolean isOutlier(BigDecimal value, List<BigDecimal> history) {
        if (value == null || history == null || history.size() < MIN_HISTORY_SAMPLES) {
            return false;
        }

        MathContext mc = new MathContext(10, RoundingMode.HALF_UP);
        BigDecimal n = BigDecimal.valueOf(history.size());
        BigDecimal sum = history.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(n, mc);

        BigDecimal varianceSum = BigDecimal.ZERO;
        for (BigDecimal v : history) {
            BigDecimal diff = v.subtract(mean);
            varianceSum = varianceSum.add(diff.multiply(diff));
        }
        BigDecimal variance = varianceSum.divide(n, mc);
        double stddev = Math.sqrt(variance.doubleValue());

        BigDecimal threshold = mean.add(BigDecimal.valueOf(2.0 * stddev));
        return value.compareTo(threshold) > 0;
    }

    // ----------------------------------------------------------------
    // Reads
    // ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AlertResponse> listByHousehold(Long householdId) {
        if (!householdRepository.existsById(householdId)) {
            throw new ResourceNotFoundException("Household", householdId);
        }
        return alertRepository.findAllByHouseholdIdOrderByCreatedAtDesc(householdId).stream()
                .map(AlertService::toResponse)
                .toList();
    }

    // ----------------------------------------------------------------
    // Persist + notify
    // ----------------------------------------------------------------

    private void persistAndNotify(Household household, AlertType type, String message,
                                   BigDecimal usageLiters, LocalDate readingDate) {
        Alert alert = Alert.builder()
                .household(household)
                .alertType(type)
                .message(message)
                .usageLiters(usageLiters)
                .readingDate(readingDate)
                .acknowledged(false)
                .build();
        alertRepository.save(alert);

        String recipient = resolveRecipient(household);
        notificationService.notifyAlert(recipient,
                "[SmartWater] " + type.name().replace('_', ' '),
                message);
    }

    private String resolveRecipient(Household household) {
        return userRepository.findFirstByHouseholdId(household.getId())
                .map(User::getEmail)
                .orElseGet(() -> household.getApartment() != null
                        ? household.getApartment().getAdminContact()
                        : "admin@smartwater.local");
    }

    public static AlertResponse toResponse(Alert alert) {
        return AlertResponse.builder()
                .id(alert.getId())
                .householdId(alert.getHousehold().getId())
                .flatNumber(alert.getHousehold().getFlatNumber())
                .alertType(alert.getAlertType())
                .message(alert.getMessage())
                .usageLiters(alert.getUsageLiters())
                .readingDate(alert.getReadingDate())
                .acknowledged(alert.getAcknowledged())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
