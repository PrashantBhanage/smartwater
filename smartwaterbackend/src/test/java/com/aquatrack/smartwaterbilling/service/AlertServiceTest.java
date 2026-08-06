package com.aquatrack.smartwaterbilling.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.aquatrack.smartwaterbilling.entity.Alert;
import com.aquatrack.smartwaterbilling.entity.Household;
import com.aquatrack.smartwaterbilling.entity.WaterUsageLog;
import com.aquatrack.smartwaterbilling.entity.enums.AlertType;
import com.aquatrack.smartwaterbilling.repository.AlertRepository;
import com.aquatrack.smartwaterbilling.repository.HouseholdRepository;
import com.aquatrack.smartwaterbilling.repository.UserRepository;
import com.aquatrack.smartwaterbilling.repository.WaterUsageLogRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AlertService#isOutlier} — 2σ leak detection math.
 */
@DisplayName("AlertService — outlier / leak detection")
class AlertServiceTest {

    @Mock private AlertRepository alertRepository;
    @Mock private HouseholdRepository householdRepository;
    @Mock private WaterUsageLogRepository usageLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private AlertService alertService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Enable the scheduler guard so scheduled methods run in unit tests
        // (the @Value field defaults to false outside a Spring context).
        ReflectionTestUtils.setField(alertService, "schedulerEnabled", true);
    }

    private static List<BigDecimal> constantHistory(double value, int n) {
        return IntStream.range(0, n)
                .mapToObj(i -> BigDecimal.valueOf(value))
                .toList();
    }

    @Test
    @DisplayName("null / insufficient history → not outlier")
    void insufficientHistory_notOutlier() {
        assertThat(alertService.isOutlier(BigDecimal.valueOf(999), null)).isFalse();
        assertThat(alertService.isOutlier(null, constantHistory(100, 10))).isFalse();
        assertThat(alertService.isOutlier(BigDecimal.valueOf(999), constantHistory(100, 6))).isFalse();
    }

    @Test
    @DisplayName("exactly MIN_HISTORY_SAMPLES of constant values: same value → not outlier")
    void constantHistory_sameValue_notOutlier() {
        List<BigDecimal> history = constantHistory(200, AlertService.MIN_HISTORY_SAMPLES);
        assertThat(alertService.isOutlier(BigDecimal.valueOf(200), history)).isFalse();
    }

    @Test
    @DisplayName("constant history (σ=0): any strictly higher value → outlier")
    void constantHistory_higherValue_isOutlier() {
        List<BigDecimal> history = constantHistory(200, AlertService.MIN_HISTORY_SAMPLES);
        assertThat(alertService.isOutlier(BigDecimal.valueOf(200.01), history)).isTrue();
        assertThat(alertService.isOutlier(BigDecimal.valueOf(500), history)).isTrue();
    }

    @Test
    @DisplayName("value within mean+2σ band → not outlier")
    void withinTwoSigma_notOutlier() {
        // history: 100,110,120,130,140,150,160  mean≈130, roughly σ≈20
        List<BigDecimal> history = List.of(
                bd(100), bd(110), bd(120), bd(130), bd(140), bd(150), bd(160));
        // 150 is well within mean+2σ
        assertThat(alertService.isOutlier(bd(150), history)).isFalse();
    }

    @Test
    @DisplayName("value clearly above mean+2σ → outlier")
    void aboveTwoSigma_isOutlier() {
        List<BigDecimal> history = List.of(
                bd(100), bd(110), bd(120), bd(130), bd(140), bd(150), bd(160));
        // 500 is far above mean+2σ (~170)
        assertThat(alertService.isOutlier(bd(500), history)).isTrue();
    }

    @Test
    @DisplayName("value exactly at mean+2σ boundary → not outlier (strict >)")
    void exactlyAtTwoSigma_notOutlier() {
        // Construct history where mean and σ are easy:
        // seven copies of 100 → mean=100, σ=0, threshold=100
        // Already covered by constant case; for non-zero σ use known set.
        // With σ=0, threshold = mean; value == mean is NOT > threshold.
        List<BigDecimal> history = constantHistory(100, 7);
        assertThat(alertService.isOutlier(bd(100), history)).isFalse();
    }

    // ----------------------------------------------------------------
    // G11 — Threshold boundary tests (just under / at / over)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Threshold: usage just under daily limit → no alert")
    void threshold_justUnder_noAlert() {
        Household h = Household.builder()
                .id(1L).flatNumber("A-101")
                .dailyThresholdLiters(BigDecimal.valueOf(500.00))
                .build();

        alertService.checkThreshold(h, BigDecimal.valueOf(499.99), LocalDate.of(2024, 6, 10));

        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    @DisplayName("Threshold: usage exactly at daily limit → no alert (strict >)")
    void threshold_atLimit_noAlert() {
        Household h = Household.builder()
                .id(1L).flatNumber("A-101")
                .dailyThresholdLiters(BigDecimal.valueOf(500.00))
                .build();

        alertService.checkThreshold(h, BigDecimal.valueOf(500.00), LocalDate.of(2024, 6, 10));

        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    @DisplayName("Threshold: usage over daily limit → alert created with MEDIUM severity")
    void threshold_over_createsAlert() {
        Household h = Household.builder()
                .id(1L).flatNumber("A-101")
                .dailyThresholdLiters(BigDecimal.valueOf(500.00))
                .build();

        when(alertRepository.existsByHouseholdIdAndAlertTypeAndReadingDate(
                eq(1L), eq(AlertType.THRESHOLD_EXCEEDED), eq(LocalDate.of(2024, 6, 10))))
                .thenReturn(false);

        alertService.checkThreshold(h, BigDecimal.valueOf(500.01), LocalDate.of(2024, 6, 10));

        verify(alertRepository).save(any(Alert.class));
    }

    // ----------------------------------------------------------------
    // G13 — checkDailyThresholds (yesterday's usage → alert on exceed)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("checkDailyThresholds: yesterday's over-limit usage creates alert")
    void checkDailyThresholds_overLimit_createsAlert() {
        Household h = Household.builder()
                .id(1L).flatNumber("A-101")
                .dailyThresholdLiters(BigDecimal.valueOf(500.00))
                .build();
        when(householdRepository.findAll()).thenReturn(List.of(h));

        LocalDate yesterday = LocalDate.now().minusDays(1);
        WaterUsageLog log = WaterUsageLog.builder()
                .volumeUsedLiters(BigDecimal.valueOf(600.00))
                .readingDate(yesterday)
                .build();
        when(usageLogRepository.findAllByHouseholdIdAndReadingDate(1L, yesterday))
                .thenReturn(List.of(log));
        when(alertRepository.existsByHouseholdIdAndAlertTypeAndReadingDate(
                eq(1L), eq(AlertType.THRESHOLD_EXCEEDED), eq(yesterday)))
                .thenReturn(false);

        alertService.checkDailyThresholds();

        verify(alertRepository).save(any(Alert.class));
    }

    @Test
    @DisplayName("checkDailyThresholds: yesterday's at-limit usage → no alert")
    void checkDailyThresholds_atLimit_noAlert() {
        Household h = Household.builder()
                .id(1L).flatNumber("A-101")
                .dailyThresholdLiters(BigDecimal.valueOf(500.00))
                .build();
        when(householdRepository.findAll()).thenReturn(List.of(h));

        LocalDate yesterday = LocalDate.now().minusDays(1);
        WaterUsageLog log = WaterUsageLog.builder()
                .volumeUsedLiters(BigDecimal.valueOf(500.00))
                .readingDate(yesterday)
                .build();
        when(usageLogRepository.findAllByHouseholdIdAndReadingDate(1L, yesterday))
                .thenReturn(List.of(log));

        alertService.checkDailyThresholds();

        verify(alertRepository, never()).save(any(Alert.class));
    }

    // ----------------------------------------------------------------
    // G12 — evaluateLeakForHousehold with realistic 30-day dataset
    // ----------------------------------------------------------------

    @Test
    @DisplayName("evaluateLeakForHousehold: normal household → no flag")
    void evaluateLeak_normalHousehold_noFlag() {
        Household h = Household.builder().id(1L).flatNumber("A-101").build();
        // 30 days of stable ~100 L usage (no spike)
        List<WaterUsageLog> logs = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            logs.add(WaterUsageLog.builder()
                    .volumeUsedLiters(BigDecimal.valueOf(100))
                    .readingDate(LocalDate.now().minusDays(30 - i))
                    .build());
        }
        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(
                eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(logs);

        boolean flagged = alertService.evaluateLeakForHousehold(h);

        assertThat(flagged).isFalse();
        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    @DisplayName("evaluateLeakForHousehold: injected outlier → flagged")
    void evaluateLeak_outlier_flagged() {
        Household h = Household.builder().id(1L).flatNumber("A-101").build();
        // 29 days of ~100 L, then one 1000 L spike
        List<WaterUsageLog> logs = new ArrayList<>();
        for (int i = 0; i < 29; i++) {
            logs.add(WaterUsageLog.builder()
                    .volumeUsedLiters(BigDecimal.valueOf(100))
                    .readingDate(LocalDate.now().minusDays(29 - i))
                    .build());
        }
        logs.add(WaterUsageLog.builder()
                .volumeUsedLiters(BigDecimal.valueOf(1000))
                .readingDate(LocalDate.now())
                .build());
        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(
                eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(logs);
        when(alertRepository.existsByHouseholdIdAndAlertTypeAndReadingDate(
                eq(1L), eq(AlertType.LEAK_SUSPECTED), any(LocalDate.class)))
                .thenReturn(false);

        boolean flagged = alertService.evaluateLeakForHousehold(h);

        assertThat(flagged).isTrue();
        verify(alertRepository).save(any(Alert.class));
    }

    // ----------------------------------------------------------------
    // G6/G7 regression tests
    // ----------------------------------------------------------------

    @Test
    @DisplayName("G6 regression: outlier older than 30 days is NOT flagged")
    void evaluateLeak_outlierOutsideWindow_notFlagged() {
        Household h = Household.builder().id(1L).flatNumber("A-101").build();
        // Only logs within the last 30 days are returned by the repository.
        // A spike 40 days ago is outside the window and must not be considered.
        List<WaterUsageLog> logs = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            logs.add(WaterUsageLog.builder()
                    .volumeUsedLiters(BigDecimal.valueOf(100))
                    .readingDate(LocalDate.now().minusDays(30 - i))
                    .build());
        }
        // The repository only returns the 30-day window (spike 40 days ago excluded).
        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(
                eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(logs);

        boolean flagged = alertService.evaluateLeakForHousehold(h);

        assertThat(flagged).isFalse();
        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    @DisplayName("G7 regression: non-latest entry within window that qualifies IS flagged")
    void evaluateLeak_nonLatestEntry_flagged() {
        Household h = Household.builder().id(1L).flatNumber("A-101").build();
        // 28 days of ~100 L, then a 1000 L spike on day 29, then a normal 100 L on day 30.
        // The spike is NOT the latest reading, but must still be flagged.
        List<WaterUsageLog> logs = new ArrayList<>();
        for (int i = 0; i < 28; i++) {
            logs.add(WaterUsageLog.builder()
                    .volumeUsedLiters(BigDecimal.valueOf(100))
                    .readingDate(LocalDate.now().minusDays(30 - i))
                    .build());
        }
        logs.add(WaterUsageLog.builder()
                .volumeUsedLiters(BigDecimal.valueOf(1000)) // spike — not latest
                .readingDate(LocalDate.now().minusDays(1))
                .build());
        logs.add(WaterUsageLog.builder()
                .volumeUsedLiters(BigDecimal.valueOf(100)) // latest — normal
                .readingDate(LocalDate.now())
                .build());
        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(
                eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(logs);
        when(alertRepository.existsByHouseholdIdAndAlertTypeAndReadingDate(
                eq(1L), eq(AlertType.LEAK_SUSPECTED), any(LocalDate.class)))
                .thenReturn(false);

        boolean flagged = alertService.evaluateLeakForHousehold(h);

        assertThat(flagged).isTrue();
        verify(alertRepository, times(1)).save(any(Alert.class));
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
