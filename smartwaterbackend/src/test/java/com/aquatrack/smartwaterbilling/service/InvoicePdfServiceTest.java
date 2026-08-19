package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.entity.*;
import com.aquatrack.smartwaterbilling.entity.enums.InvoiceStatus;
import com.aquatrack.smartwaterbilling.entity.enums.UsageSource;
import com.aquatrack.smartwaterbilling.entity.enums.UsageStatus;
import com.aquatrack.smartwaterbilling.repository.WaterUsageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InvoicePdfService}.
 *
 * <p>All external dependencies (repositories, TariffPlanService) are mocked so the
 * tests run in-process with no Spring context, database, or PDFBox file I/O overhead.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvoicePdfService — PDF generation unit tests")
class InvoicePdfServiceTest {

    @Mock
    private WaterUsageLogRepository usageLogRepository;

    @Mock
    private TariffPlanService tariffPlanService;

    @InjectMocks
    private InvoicePdfService invoicePdfService;

    // ── Shared test fixtures ─────────────────────────────────────────────────

    private Apartment apartment;
    private Household household;
    private BillingCycle cycle;
    private TariffPlan tariffPlan;

    @BeforeEach
    void setUp() {
        apartment = Apartment.builder()
                .id(1L)
                .name("Sunrise Apartments")
                .address("123 Main Street")
                .totalHouseholds(10)
                .adminContact("admin@sunrise.com")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        household = Household.builder()
                .id(10L)
                .flatNumber("A-101")
                .areaSqft(BigDecimal.valueOf(1200))
                .occupancyCount(3)
                .hasMeter(true)
                .dailyThresholdLiters(BigDecimal.valueOf(500))
                .apartment(apartment)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        cycle = BillingCycle.builder()
                .id(5L)
                .cycleStartDate(LocalDate.of(2026, 7, 1))
                .cycleEndDate(LocalDate.of(2026, 7, 31))
                .apartment(apartment)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        tariffPlan = TariffPlan.builder()
                .id(1L)
                .tier1LimitKl(BigDecimal.valueOf(10))
                .tier1Rate(BigDecimal.valueOf(10.00))
                .tier2Rate(BigDecimal.valueOf(15.00))
                .effectiveFromDate(LocalDate.of(2026, 1, 1))
                .apartment(apartment)
                .build();
    }

    private Invoice buildInvoice(BigDecimal baseCharge, BigDecimal sharedAlloc, BigDecimal adjustments) {
        Invoice inv = Invoice.builder()
                .id(100L)
                .household(household)
                .billingCycle(cycle)
                .baseCharge(baseCharge)
                .sharedAllocation(sharedAlloc)
                .adjustments(adjustments)
                .status(InvoiceStatus.ISSUED)
                .build();
        inv.recomputeTotal();
        // Manually set createdAt since @PrePersist won't fire in unit tests
        try {
            var field = Invoice.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(inv, LocalDateTime.now());
            var upd = Invoice.class.getDeclaredField("updatedAt");
            upd.setAccessible(true);
            upd.set(inv, LocalDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return inv;
    }

    private WaterUsageLog usageLog(BigDecimal liters) {
        return WaterUsageLog.builder()
                .id(1L)
                .readingDate(LocalDate.of(2026, 7, 15))
                .volumeUsedLiters(liters)
                .source(UsageSource.MANUAL)
                .usageStatus(UsageStatus.GREEN)
                .household(household)
                .build();
    }

    // ── Test 1: non-null, non-empty byte array ───────────────────────────────

    @Test
    @DisplayName("generatePdf returns a non-empty byte array for a valid invoice")
    void generatePdf_returnsNonEmptyByteArray() throws IOException {
        // 8 kL — within tier 1
        BigDecimal usage = BigDecimal.valueOf(8_000);
        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of(usageLog(usage)));
        when(tariffPlanService.requireActivePlan(anyLong(), any()))
                .thenReturn(tariffPlan);

        Invoice invoice = buildInvoice(
                BigDecimal.valueOf(80.00),
                BigDecimal.valueOf(12.00),
                BigDecimal.ZERO);

        byte[] pdf = invoicePdfService.generatePdf(invoice);

        assertThat(pdf).isNotNull().isNotEmpty();
    }

    // ── Test 2: output is a valid PDF (magic bytes check) ───────────────────

    @Test
    @DisplayName("generatePdf output starts with PDF magic bytes (%PDF)")
    void generatePdf_pdfStartsWithMagicBytes() throws IOException {
        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of(usageLog(BigDecimal.valueOf(5_000))));
        when(tariffPlanService.requireActivePlan(anyLong(), any()))
                .thenReturn(tariffPlan);

        Invoice invoice = buildInvoice(
                BigDecimal.valueOf(50.00),
                BigDecimal.valueOf(8.00),
                BigDecimal.ZERO);

        byte[] pdf = invoicePdfService.generatePdf(invoice);

        // PDF files always start with "%PDF"
        String header = new String(pdf, 0, 4);
        assertThat(header).isEqualTo("%PDF");
    }

    // ── Test 3: null invoice → IllegalArgumentException ─────────────────────

    @Test
    @DisplayName("generatePdf throws IllegalArgumentException when invoice is null")
    void generatePdf_nullInvoice_throwsException() {
        assertThatThrownBy(() -> invoicePdfService.generatePdf(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invoice must not be null");

        // Repository and tariff service should never be called
        verifyNoInteractions(usageLogRepository, tariffPlanService);
    }

    // ── Test 4: zero consumption — no tier2 usage ───────────────────────────

    @Test
    @DisplayName("generatePdf handles zero metered consumption without errors")
    void generatePdf_zeroConsumption_succeeds() throws IOException {
        // No usage logs for this household in the cycle
        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        // TariffPlanService should NOT be called for zero consumption
        // (breakdown uses placeholder values)

        Invoice invoice = buildInvoice(
                BigDecimal.ZERO,
                BigDecimal.valueOf(5.00),
                BigDecimal.ZERO);

        byte[] pdf = invoicePdfService.generatePdf(invoice);

        assertThat(pdf).isNotNull().isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        // tariffPlanService should not be called for zero usage
        verify(tariffPlanService, never()).requireActivePlan(anyLong(), any());
    }

    // ── Test 5: tier-2 usage — TariffPlanService invoked ───────────────────

    @Test
    @DisplayName("generatePdf fetches TariffPlan when consumption exceeds tier-1 limit")
    void generatePdf_tier2Usage_fetchesTariffPlan() throws IOException {
        // 15 kL — exceeds tier1LimitKl of 10 kL, so tier 2 applies
        BigDecimal usage = BigDecimal.valueOf(15_000);
        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of(usageLog(usage)));
        when(tariffPlanService.requireActivePlan(anyLong(), any()))
                .thenReturn(tariffPlan);

        Invoice invoice = buildInvoice(
                BigDecimal.valueOf(175.00),
                BigDecimal.valueOf(20.00),
                BigDecimal.valueOf(-5.00));

        byte[] pdf = invoicePdfService.generatePdf(invoice);

        assertThat(pdf).isNotNull().isNotEmpty();
        // Verify the tariff plan was looked up for tier-2 computation
        verify(tariffPlanService, times(1)).requireActivePlan(
                eq(apartment.getId()), eq(cycle.getCycleEndDate()));
    }

    // ── Test 6: negative adjustment (credit) ────────────────────────────────

    @Test
    @DisplayName("generatePdf handles negative adjustments (credits) without errors")
    void generatePdf_negativeAdjustment_succeeds() throws IOException {
        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of(usageLog(BigDecimal.valueOf(3_000))));
        when(tariffPlanService.requireActivePlan(anyLong(), any()))
                .thenReturn(tariffPlan);

        Invoice invoice = buildInvoice(
                BigDecimal.valueOf(30.00),
                BigDecimal.valueOf(6.00),
                BigDecimal.valueOf(-10.00)); // credit

        byte[] pdf = invoicePdfService.generatePdf(invoice);

        assertThat(pdf).isNotNull().isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    // ── Test 7: unmetered household (hasMeter=false) ─────────────────────────

    @Test
    @DisplayName("generatePdf works for unmetered households (no usage logs)")
    void generatePdf_unmeteredHousehold_succeeds() throws IOException {
        household.setHasMeter(false);
        when(usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());

        Invoice invoice = buildInvoice(
                BigDecimal.ZERO,
                BigDecimal.valueOf(15.00),
                BigDecimal.ZERO);

        byte[] pdf = invoicePdfService.generatePdf(invoice);

        assertThat(pdf).isNotNull().isNotEmpty();
        // For zero consumption, tariff plan lookup is skipped
        verify(tariffPlanService, never()).requireActivePlan(anyLong(), any());
    }
}
