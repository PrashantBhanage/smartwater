package com.aquatrack.smartwaterbilling.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.aquatrack.smartwaterbilling.repository.AlertRepository;
import com.aquatrack.smartwaterbilling.repository.HouseholdRepository;
import com.aquatrack.smartwaterbilling.repository.UserRepository;
import com.aquatrack.smartwaterbilling.repository.WaterUsageLogRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
