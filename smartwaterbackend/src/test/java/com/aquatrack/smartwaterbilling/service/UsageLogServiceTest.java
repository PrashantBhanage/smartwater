package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.entity.enums.UsageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import com.aquatrack.smartwaterbilling.repository.HouseholdRepository;
import com.aquatrack.smartwaterbilling.repository.WaterUsageLogRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UsageLogService#computeUsageStatus}.
 *
 * <p>The threshold (T) for all tests below is 500 L/day.
 * Boundary table:
 * <pre>
 * Volume   │ Expected status
 * ─────────┼────────────────
 * 0        │ GREEN
 * 499.99   │ GREEN
 * 500.00   │ GREEN  (at threshold — inclusive)
 * 500.01   │ YELLOW
 * 749.99   │ YELLOW
 * 750.00   │ RED    (at 1.5× threshold — inclusive)
 * 750.01   │ RED
 * 9999     │ RED
 * </pre>
 */
@DisplayName("UsageLogService — computeUsageStatus boundary tests")
class UsageLogServiceTest {

    @Mock
    private WaterUsageLogRepository usageLogRepository;

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private AlertService alertService;

    @InjectMocks
    private UsageLogService usageLogService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static final BigDecimal THRESHOLD = BigDecimal.valueOf(500);

    // ----------------------------------------------------------------
    // Boundary: exactly at threshold → GREEN
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Volume exactly at threshold → GREEN")
    void atThreshold_returnsGreen() {
        UsageStatus status = usageLogService.computeUsageStatus(
                BigDecimal.valueOf(500), THRESHOLD);
        assertThat(status).isEqualTo(UsageStatus.GREEN);
    }

    // ----------------------------------------------------------------
    // Boundary: exactly at 1.5× threshold → RED
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Volume exactly at 1.5× threshold → RED")
    void atRedThreshold_returnsRed() {
        // 1.5 × 500 = 750
        UsageStatus status = usageLogService.computeUsageStatus(
                BigDecimal.valueOf(750), THRESHOLD);
        assertThat(status).isEqualTo(UsageStatus.RED);
    }

    // ----------------------------------------------------------------
    // Just below threshold → GREEN
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Volume just below threshold (499.99) → GREEN")
    void justBelowThreshold_returnsGreen() {
        UsageStatus status = usageLogService.computeUsageStatus(
                new BigDecimal("499.99"), THRESHOLD);
        assertThat(status).isEqualTo(UsageStatus.GREEN);
    }

    // ----------------------------------------------------------------
    // Just above threshold → YELLOW
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Volume just above threshold (500.01) → YELLOW")
    void justAboveThreshold_returnsYellow() {
        UsageStatus status = usageLogService.computeUsageStatus(
                new BigDecimal("500.01"), THRESHOLD);
        assertThat(status).isEqualTo(UsageStatus.YELLOW);
    }

    // ----------------------------------------------------------------
    // Just below 1.5× threshold → YELLOW
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Volume just below 1.5× threshold (749.99) → YELLOW")
    void justBelowRedThreshold_returnsYellow() {
        UsageStatus status = usageLogService.computeUsageStatus(
                new BigDecimal("749.99"), THRESHOLD);
        assertThat(status).isEqualTo(UsageStatus.YELLOW);
    }

    // ----------------------------------------------------------------
    // Just above 1.5× threshold → RED
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Volume just above 1.5× threshold (750.01) → RED")
    void justAboveRedThreshold_returnsRed() {
        UsageStatus status = usageLogService.computeUsageStatus(
                new BigDecimal("750.01"), THRESHOLD);
        assertThat(status).isEqualTo(UsageStatus.RED);
    }

    // ----------------------------------------------------------------
    // Zero usage → GREEN
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Zero volume usage → GREEN")
    void zeroVolume_returnsGreen() {
        UsageStatus status = usageLogService.computeUsageStatus(
                BigDecimal.ZERO, THRESHOLD);
        assertThat(status).isEqualTo(UsageStatus.GREEN);
    }

    // ----------------------------------------------------------------
    // Very high usage → RED
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Very high usage (9999L) → RED")
    void veryHighUsage_returnsRed() {
        UsageStatus status = usageLogService.computeUsageStatus(
                BigDecimal.valueOf(9999), THRESHOLD);
        assertThat(status).isEqualTo(UsageStatus.RED);
    }

    // ----------------------------------------------------------------
    // Parameterised — midpoint of each band
    // ----------------------------------------------------------------

    @ParameterizedTest(name = "volume={0}L, threshold={1}L → {2}")
    @CsvSource({
        "250,    500,    GREEN",   // Well within normal
        "500,    500,    GREEN",   // Exactly at threshold
        "600,    500,    YELLOW",  // Mid-YELLOW
        "749,    500,    YELLOW",  // Near RED boundary
        "750,    500,    RED",     // Exactly RED threshold
        "1000,   500,    RED",     // Deep RED

        // Different threshold (200L)
        "0,      200,    GREEN",
        "200,    200,    GREEN",
        "201,    200,    YELLOW",
        "299,    200,    YELLOW",
        "300,    200,    RED",
        "500,    200,    RED",
    })
    @DisplayName("Parameterised boundary sweep")
    void parameterisedBoundary(double volume, double threshold, UsageStatus expected) {
        UsageStatus status = usageLogService.computeUsageStatus(
                BigDecimal.valueOf(volume), BigDecimal.valueOf(threshold));
        assertThat(status).isEqualTo(expected);
    }
}
