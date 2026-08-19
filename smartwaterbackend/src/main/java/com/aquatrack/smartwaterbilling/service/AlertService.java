package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.dto.alert.AlertResponse;
import com.aquatrack.smartwaterbilling.entity.Alert;
import com.aquatrack.smartwaterbilling.entity.Household;
import com.aquatrack.smartwaterbilling.entity.User;
import com.aquatrack.smartwaterbilling.entity.WaterUsageLog;
import com.aquatrack.smartwaterbilling.entity.enums.AlertSeverity;
import com.aquatrack.smartwaterbilling.entity.enums.AlertType;
import com.aquatrack.smartwaterbilling.exception.ResourceNotFoundException;
import com.aquatrack.smartwaterbilling.repository.AlertRepository;
import com.aquatrack.smartwaterbilling.repository.ApartmentRepository;
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
import java.time.LocalDateTime;
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
    private final ApartmentRepository apartmentRepository;
    private final HouseholdRepository householdRepository;
    private final WaterUsageLogRepository usageLogRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailNotificationService emailNotificationService;


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
                "ALERT: Household %s exceeded daily threshold: %.2f L > %.2f L on %s",
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
     * Checks daily water usage thresholds. Runs daily at midnight.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void checkDailyThresholds() {
        if (!schedulerEnabled) {
            return;
        }
        LocalDate yesterday = LocalDate.now().minusDays(1);
        for (Household household : householdRepository.findAll()) {
            List<WaterUsageLog> logs = usageLogRepository.findAllByHouseholdIdAndReadingDate(household.getId(), yesterday);
            for (WaterUsageLog log : logs) {
                checkThreshold(household, log.getVolumeUsedLiters(), yesterday);
            }
        }
    }

    /**
     * Detects water consumption anomalies using standard deviation metric. Runs daily at 1 AM.
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void detectAnomalies() {
        scanForLeaks();
    }


    /**
     * Evaluates a single household for leak suspicion over the last 30 days.
     * Package-visible for tests.
     *
     * <p>Computes mean + 2σ over the 30-day window and flags <em>every</em> log
     * entry whose volume exceeds that threshold (not just the latest reading).
     * Households with fewer than {@link #MIN_HISTORY_SAMPLES} readings in the
     * window are skipped (insufficient data — no crash, no false-flag).</p>
     *
     * @return true if at least one new LEAK_SUSPECTED alert was created
     */
    @Transactional
    public boolean evaluateLeakForHousehold(Household household) {
        LocalDate windowStart = LocalDate.now().minusDays(30);
        List<WaterUsageLog> logs = usageLogRepository
                .findAllByHouseholdIdAndReadingDateBetween(household.getId(), windowStart, LocalDate.now());
        if (logs.size() < MIN_HISTORY_SAMPLES + 1) {
            return false; // need history + one candidate reading in the 30-day window
        }

        logs = new ArrayList<>(logs);
        logs.sort(Comparator.comparing(WaterUsageLog::getReadingDate));

        // Compute mean + 2σ over the entire 30-day window.
        List<BigDecimal> volumes = logs.stream()
                .map(WaterUsageLog::getVolumeUsedLiters)
                .toList();
        BigDecimal threshold = computeOutlierThreshold(volumes);

        boolean flaggedAny = false;
        for (WaterUsageLog log : logs) {
            BigDecimal volume = log.getVolumeUsedLiters();
            if (volume == null || volume.compareTo(threshold) <= 0) {
                continue;
            }
            // Deduplicate: one leak alert per household per reading date
            if (alertRepository.existsByHouseholdIdAndAlertTypeAndReadingDate(
                    household.getId(), AlertType.LEAK_SUSPECTED, log.getReadingDate())) {
                continue;
            }

            String message = String.format(
                    "ANOMALY: Household %s usage spike detected (%.2f liters) on %s — > 2σ above 30-day average",
                    household.getFlatNumber(),
                    volume,
                    log.getReadingDate());

            persistAndNotify(household, AlertType.LEAK_SUSPECTED, message,
                    volume, log.getReadingDate());
            flaggedAny = true;
        }
        return flaggedAny;
    }

    /**
     * Computes the outlier threshold (mean + 2×stddev) for a list of volumes.
     * Requires at least {@link #MIN_HISTORY_SAMPLES} values; otherwise returns
     * {@code null} (caller treats null as "no threshold → nothing flagged").
     */
    private BigDecimal computeOutlierThreshold(List<BigDecimal> volumes) {
        if (volumes == null || volumes.size() < MIN_HISTORY_SAMPLES) {
            return null;
        }

        MathContext mc = new MathContext(10, RoundingMode.HALF_UP);
        BigDecimal n = BigDecimal.valueOf(volumes.size());
        BigDecimal sum = volumes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(n, mc);

        BigDecimal varianceSum = BigDecimal.ZERO;
        for (BigDecimal v : volumes) {
            BigDecimal diff = v.subtract(mean);
            varianceSum = varianceSum.add(diff.multiply(diff));
        }
        BigDecimal variance = varianceSum.divide(n, mc);
        double stddev = Math.sqrt(variance.doubleValue());

        return mean.add(BigDecimal.valueOf(2.0 * stddev));
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

    @Transactional(readOnly = true)
    public List<AlertResponse> listRecentAlertsForApartment(Long apartmentId, int days) {
        if (!apartmentRepository.existsById(apartmentId)) {
            throw new ResourceNotFoundException("Apartment", apartmentId);
        }
        LocalDateTime sinceDate = LocalDateTime.now().minusDays(days);
        return alertRepository.findAllByApartmentIdRecent(apartmentId, sinceDate).stream()
                .map(AlertService::toResponse)
                .toList();
    }

    // ----------------------------------------------------------------
    // Persist + notify
    // ----------------------------------------------------------------

    private void persistAndNotify(Household household, AlertType type, String message,
                                   BigDecimal usageLiters, LocalDate readingDate) {
        AlertSeverity severity = type == AlertType.THRESHOLD_EXCEEDED
                ? AlertSeverity.MEDIUM
                : AlertSeverity.HIGH;
        Alert alert = Alert.builder()
                .household(household)
                .alertType(type)
                .severity(severity)
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

        if (emailNotificationService != null) {
            try {
                if (type == AlertType.THRESHOLD_EXCEEDED) {
                    emailNotificationService.sendOveruseWarning(household, usageLiters, readingDate);
                } else if (type == AlertType.LEAK_SUSPECTED) {
                    emailNotificationService.sendLeakAlert(household, usageLiters, readingDate);
                }
            } catch (Exception e) {
                log.warn("Failed to dispatch email alert for type {}: {}", type, e.getMessage());
            }
        }
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
                .severity(alert.getSeverity())
                .message(alert.getMessage())
                .usageLiters(alert.getUsageLiters())
                .readingDate(alert.getReadingDate())
                .acknowledged(alert.getAcknowledged())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
