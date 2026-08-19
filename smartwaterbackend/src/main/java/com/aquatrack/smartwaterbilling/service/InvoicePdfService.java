package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.entity.Invoice;
import com.aquatrack.smartwaterbilling.entity.TariffPlan;
import com.aquatrack.smartwaterbilling.entity.WaterUsageLog;
import com.aquatrack.smartwaterbilling.repository.WaterUsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates a professional PDF invoice for a finalized {@link Invoice}.
 *
 * <p>Layout (A4, portrait):
 * <ol>
 *   <li>Header — company name, invoice number, generated date</li>
 *   <li>Household details — flat, apartment, billing period</li>
 *   <li>Metered consumption breakdown — total kL, tier 1, tier 2 charge</li>
 *   <li>Shared-area water cost allocation (garden / pool / lobby)</li>
 *   <li>Adjustments</li>
 *   <li>Total amount due (highlighted)</li>
 *   <li>Payment instructions</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoicePdfService {

    private final WaterUsageLogRepository usageLogRepository;
    private final TariffPlanService tariffPlanService;

    // -- Layout constants ---------------------------------------------------
    private static final float MARGIN        = 50f;
    private static final float PAGE_WIDTH    = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT   = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final float LINE_HEIGHT   = 16f;
    private static final float SECTION_GAP   = 10f;

    private static final Color COLOR_HEADER_BG  = new Color(0x1A, 0x73, 0xE8);
    private static final Color COLOR_HEADER_FG  = Color.WHITE;
    private static final Color COLOR_TOTAL_BG   = new Color(0x0D, 0x47, 0xA1);
    private static final Color COLOR_TOTAL_FG   = Color.WHITE;
    private static final Color COLOR_ROW_ALT    = new Color(0xF5, 0xF5, 0xF5);
    private static final Color COLOR_BORDER     = new Color(0xCC, 0xCC, 0xCC);
    private static final Color COLOR_TEXT       = new Color(0x21, 0x21, 0x21);
    private static final Color COLOR_MUTED      = new Color(0x75, 0x75, 0x75);

    private static final BigDecimal LITERS_PER_KL = BigDecimal.valueOf(1000);
    private static final int        MONEY_SCALE   = 2;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // -- Public API ---------------------------------------------------------

    /**
     * Generates a PDF invoice for the given {@link Invoice} and returns the raw bytes.
     *
     * <p>Execution is wrapped so that unexpected PDFBox/rendering failures are logged
     * and surfaced as a clear {@link IllegalStateException} instead of a raw,
     * unexplained exception propagating out of the HTTP layer.
     *
     * @param invoice fully-loaded Invoice (with household, billingCycle, apartment relations)
     * @return PDF as a byte array
     * @throws IllegalArgumentException if invoice is null
     */
    @Transactional(readOnly = true)
    public byte[] generatePdf(Invoice invoice) {
        if (invoice == null) {
            throw new IllegalArgumentException("Invoice must not be null");
        }
        try {
            return renderPdf(invoice);
        } catch (IOException e) {
            log.error("PDF generation failed for invoice id={}", invoice.getId(), e);
            throw new IllegalStateException("Failed to generate invoice PDF for id=" + invoice.getId(), e);
        } catch (RuntimeException e) {
            log.error("PDF generation failed for invoice id={}", invoice.getId(), e);
            throw e;
        }
    }

    private byte[] renderPdf(Invoice invoice) throws IOException {
        var household = invoice.getHousehold();
        var cycle     = invoice.getBillingCycle();
        var apartment = household != null ? household.getApartment() : null;

        Long householdId = household != null ? household.getId() : null;
        Long apartmentId = apartment != null ? apartment.getId() : null;

        List<WaterUsageLog> logs = (householdId != null && cycle != null
                && cycle.getCycleStartDate() != null && cycle.getCycleEndDate() != null)
                ? usageLogRepository.findAllByHouseholdIdAndReadingDateBetween(
                        householdId,
                        cycle.getCycleStartDate(),
                        cycle.getCycleEndDate())
                : List.of();

        BigDecimal totalLiters = logs.stream()
                .map(l -> l.getVolumeUsedLiters() != null ? l.getVolumeUsedLiters() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalKl = totalLiters.divide(LITERS_PER_KL, 4, RoundingMode.HALF_UP);

        TariffBreakdown breakdown = computeBreakdown(totalLiters, apartmentId, cycle);

        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            String aptName = apartment != null && apartment.getName() != null ? apartment.getName() : "Apartment Complex";

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PAGE_HEIGHT - MARGIN;
                y = drawHeader(cs, invoice, aptName, y);
                y -= SECTION_GAP;
                y = drawHouseholdDetails(cs, household, apartment, cycle, y);
                y -= SECTION_GAP;
                y = drawConsumptionBreakdown(cs, totalKl, breakdown, y);
                y -= SECTION_GAP;
                y = drawSharedAllocation(cs, invoice, y);
                y -= SECTION_GAP;
                y = drawAdjustments(cs, invoice, y);
                y -= SECTION_GAP;
                y = drawTotalDue(cs, invoice, y);
                y -= SECTION_GAP * 2;
                y = drawPaymentInstructions(cs, y);
                drawFooter(cs);
            }

            doc.save(baos);
            return baos.toByteArray();
        }
    }

    // -- Private drawing helpers --------------------------------------------

    private float drawHeader(PDPageContentStream cs, Invoice invoice,
                             String apartmentName, float y) throws IOException {
        float bannerH = 70f;
        float bannerY = y - bannerH;

        fillRect(cs, MARGIN, bannerY, CONTENT_WIDTH, bannerH, COLOR_HEADER_BG);

        font(cs, Standard14Fonts.FontName.HELVETICA_BOLD, 18, COLOR_HEADER_FG);
        text(cs, "AquaTrack", MARGIN + 12, bannerY + bannerH - 28);

        font(cs, Standard14Fonts.FontName.HELVETICA, 10, COLOR_HEADER_FG);
        text(cs, "Smart Water Usage & Billing System", MARGIN + 12, bannerY + bannerH - 46);
        text(cs, apartmentName, MARGIN + 12, bannerY + bannerH - 60);

        float rightX = PAGE_WIDTH - MARGIN - 180;
        font(cs, Standard14Fonts.FontName.HELVETICA_BOLD, 14, COLOR_HEADER_FG);
        text(cs, "INVOICE", rightX, bannerY + bannerH - 25);

        font(cs, Standard14Fonts.FontName.HELVETICA, 9, COLOR_HEADER_FG);
        String invoiceNumber = invoice.getId() != null ? String.valueOf(invoice.getId()) : "-";
        text(cs, "Invoice #: " + invoiceNumber, rightX, bannerY + bannerH - 40);
        LocalDateTime createdAt = invoice.getCreatedAt() != null ? invoice.getCreatedAt() : LocalDateTime.now();
        String statusStr = invoice.getStatus() != null ? invoice.getStatus().name() : "ISSUED";
        text(cs, "Issued:    " + createdAt.format(DATE_FMT), rightX, bannerY + bannerH - 53);
        text(cs, "Status:    " + statusStr, rightX, bannerY + bannerH - 66);

        return bannerY - 4;
    }

    private float drawHouseholdDetails(PDPageContentStream cs,
                                       com.aquatrack.smartwaterbilling.entity.Household household,
                                       com.aquatrack.smartwaterbilling.entity.Apartment apartment,
                                       com.aquatrack.smartwaterbilling.entity.BillingCycle cycle,
                                       float y) throws IOException {
        y = drawSectionBanner(cs, "Household Details", null, y);

        String billingPeriod = cycle != null && cycle.getCycleStartDate() != null && cycle.getCycleEndDate() != null
                ? cycle.getCycleStartDate().format(DATE_FMT) + " - " + cycle.getCycleEndDate().format(DATE_FMT)
                : "-";

        String[] labels = {"Apartment:", "Flat / Unit:", "Billing Period:",
                           "Metered:",   "Area (sqft):", "Occupancy:"};
        String[] values = {
                apartment != null && apartment.getName() != null ? apartment.getName() : "-",
                household != null && household.getFlatNumber() != null ? household.getFlatNumber() : "-",
                billingPeriod,
                household != null && Boolean.TRUE.equals(household.getHasMeter()) ? "Yes" : "No",
                household != null && household.getAreaSqft() != null ? household.getAreaSqft().toPlainString() : "-",
                household != null && household.getOccupancyCount() != null
                        ? String.valueOf(household.getOccupancyCount()) : "-"
        };

        float colLeft  = MARGIN + 10;
        float colRight = MARGIN + CONTENT_WIDTH / 2 + 10;
        float startY   = y;

        for (int i = 0; i < labels.length; i++) {
            float rowY = startY - (i % 3) * LINE_HEIGHT;
            float colX = (i < 3) ? colLeft : colRight;
            font(cs, Standard14Fonts.FontName.HELVETICA_BOLD, 9, COLOR_MUTED);
            text(cs, labels[i], colX, rowY);
            font(cs, Standard14Fonts.FontName.HELVETICA, 9, COLOR_TEXT);
            text(cs, values[i], colX + 90, rowY);
        }

        return startY - 3 * LINE_HEIGHT - 4;
    }

    private float drawConsumptionBreakdown(PDPageContentStream cs,
                                           BigDecimal totalKl,
                                           TariffBreakdown b,
                                           float y) throws IOException {
        y = drawSectionBanner(cs, "Metered Water Consumption", null, y);

        String[][] rows = {
                {"Total Metered Consumption",
                 totalKl.setScale(4, RoundingMode.HALF_UP).toPlainString() + " kL", ""},
                {"Tier 1 (<= " + b.tier1LimitKl().toPlainString() + " kL) @ Rs." + b.tier1Rate().toPlainString() + "/kL",
                 b.tier1Kl().setScale(4, RoundingMode.HALF_UP).toPlainString() + " kL",
                 "Rs. " + b.tier1Charge().toPlainString()},
                {"Tier 2 (> " + b.tier1LimitKl().toPlainString() + " kL) @ Rs." + b.tier2Rate().toPlainString() + "/kL",
                 b.tier2Kl().setScale(4, RoundingMode.HALF_UP).toPlainString() + " kL",
                 "Rs. " + b.tier2Charge().toPlainString()},
                {"Base Charge Subtotal", "", "Rs. " + b.totalCharge().toPlainString()}
        };

        float[] colWidths = {CONTENT_WIDTH * 0.55f, CONTENT_WIDTH * 0.22f, CONTENT_WIDTH * 0.23f};
        return drawTable(cs, new String[]{"Description", "Volume", "Amount"}, rows, colWidths, y, true);
    }

    private float drawSharedAllocation(PDPageContentStream cs, Invoice invoice, float y) throws IOException {
        y = drawSectionBanner(cs, "Shared-Area Water Cost Allocation",
                "Proportional share of communal water usage (garden, pool, lobby)", y);

        float[] colWidths = {CONTENT_WIDTH * 0.77f, CONTENT_WIDTH * 0.23f};
        BigDecimal shared = invoice.getSharedAllocation() != null ? invoice.getSharedAllocation() : BigDecimal.ZERO;
        String[][] rows = {
                {"Shared-area allocation (based on flat size / occupancy)",
                 "Rs. " + shared.setScale(MONEY_SCALE, RoundingMode.HALF_UP).toPlainString()}
        };
        return drawTable(cs, new String[]{"Description", "Amount"}, rows, colWidths, y, false);
    }

    private float drawAdjustments(PDPageContentStream cs, Invoice invoice, float y) throws IOException {
        y = drawSectionBanner(cs, "Adjustments", null, y);

        float[] colWidths = {CONTENT_WIDTH * 0.77f, CONTENT_WIDTH * 0.23f};
        BigDecimal adj = invoice.getAdjustments() != null ? invoice.getAdjustments() : BigDecimal.ZERO;
        String adjDisplay = (adj.compareTo(BigDecimal.ZERO) < 0)
                ? "- Rs. " + adj.abs().setScale(MONEY_SCALE, RoundingMode.HALF_UP).toPlainString()
                : "Rs. " + adj.setScale(MONEY_SCALE, RoundingMode.HALF_UP).toPlainString();
        String[][] rows = {
                {"Manual credits / debits applied at cycle finalization", adjDisplay}
        };
        return drawTable(cs, new String[]{"Description", "Amount"}, rows, colWidths, y, false);
    }

    private float drawTotalDue(PDPageContentStream cs, Invoice invoice, float y) throws IOException {
        float rowH = 26f;
        fillRect(cs, MARGIN, y - rowH, CONTENT_WIDTH, rowH, COLOR_TOTAL_BG);
        drawRectBorder(cs, MARGIN, y - rowH, CONTENT_WIDTH, rowH, COLOR_TOTAL_BG);

        font(cs, Standard14Fonts.FontName.HELVETICA_BOLD, 12, COLOR_TOTAL_FG);
        text(cs, "TOTAL AMOUNT DUE", MARGIN + 10, y - rowH + 9);

        BigDecimal totalAmt = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        String total = "Rs. " + totalAmt.setScale(MONEY_SCALE, RoundingMode.HALF_UP).toPlainString();
        float totalW = approximateTextWidth(total, 12);
        text(cs, total, PAGE_WIDTH - MARGIN - totalW - 10, y - rowH + 9);

        return y - rowH;
    }

    private float drawPaymentInstructions(PDPageContentStream cs, float y) throws IOException {
        y = drawSectionBanner(cs, "Payment Instructions", null, y);

        String[] lines = {
                "Please pay the total amount due within 30 days from the invoice issue date.",
                "",
                "Bank Transfer : AquaTrack Water Society Bank | A/C: 0012-3456-7890 | IFSC: AQUA0001234",
                "Online Portal : https://aquatrack.example.com/pay  (use Invoice # as reference)",
                "UPI           : aquatrack@upi",
                "",
                "For queries contact your apartment administrator or email billing@aquatrack.example.com."
        };

        for (String line : lines) {
            if (line.isEmpty()) {
                y -= LINE_HEIGHT * 0.5f;
                continue;
            }
            font(cs, Standard14Fonts.FontName.HELVETICA, 8, COLOR_TEXT);
            text(cs, line, MARGIN + 10, y);
            y -= LINE_HEIGHT;
        }
        return y;
    }

    private void drawFooter(PDPageContentStream cs) throws IOException {
        float footerY = MARGIN - 10;
        cs.setStrokingColor(COLOR_BORDER);
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN, footerY + 10);
        cs.lineTo(PAGE_WIDTH - MARGIN, footerY + 10);
        cs.stroke();

        font(cs, Standard14Fonts.FontName.HELVETICA_OBLIQUE, 7, COLOR_MUTED);
        text(cs, "Generated by AquaTrack Smart Water Billing System  -  Page 1 of 1", MARGIN, footerY);
        String ts = "Generated: " + java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"));
        float tsW = approximateTextWidth(ts, 7);
        text(cs, ts, PAGE_WIDTH - MARGIN - tsW, footerY);
    }

    // -- Reusable drawing primitives ----------------------------------------

    private float drawSectionBanner(PDPageContentStream cs, String title, String subtitle, float currentY) throws IOException {
        cs.setNonStrokingColor(new Color(230, 240, 255));
        cs.addRect(40, currentY - 18, 530, subtitle != null ? 28 : 20);
        cs.fill();

        cs.beginText();
        cs.setNonStrokingColor(new Color(24, 119, 242));
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
        cs.newLineAtOffset(45, currentY - 10);
        cs.showText(title);
        cs.endText();

        if (subtitle != null) {
            cs.beginText();
            cs.setNonStrokingColor(Color.DARK_GRAY);
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
            cs.newLineAtOffset(45, currentY - 22);
            cs.showText(subtitle);
            cs.endText();
            return currentY - 35; // Returns clear Y position below banner & subtitle
        }
        return currentY - 25; // Returns clear Y position below banner
    }

    private float drawTable(PDPageContentStream cs, String[] headers, String[][] rows,
                            float[] colWidths, float y, boolean showHeader) throws IOException {
        float rowH = LINE_HEIGHT;
        float x;

        if (showHeader) {
            fillRect(cs, MARGIN, y - rowH, CONTENT_WIDTH, rowH, new Color(0xD0, 0xD8, 0xF0));
            drawRectBorder(cs, MARGIN, y - rowH, CONTENT_WIDTH, rowH, COLOR_BORDER);
            x = MARGIN;
            for (int c = 0; c < headers.length; c++) {
                font(cs, Standard14Fonts.FontName.HELVETICA_BOLD, 8, COLOR_TEXT);
                text(cs, headers[c], x + 4, y - rowH + 5);
                x += colWidths[c];
            }
            y -= rowH;
        }

        for (int r = 0; r < rows.length; r++) {
            Color bg = (r % 2 == 0) ? Color.WHITE : COLOR_ROW_ALT;
            fillRect(cs, MARGIN, y - rowH, CONTENT_WIDTH, rowH, bg);
            drawRectBorder(cs, MARGIN, y - rowH, CONTENT_WIDTH, rowH, COLOR_BORDER);
            x = MARGIN;
            for (int c = 0; c < rows[r].length; c++) {
                boolean isLast = (c == rows[r].length - 1);
                font(cs, isLast ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA,
                        8, COLOR_TEXT);
                text(cs, rows[r][c], x + 4, y - rowH + 5);
                x += colWidths[c];
            }
            y -= rowH;
        }
        return y;
    }

    // -- Low-level PDFBox helpers -------------------------------------------

    private void fillRect(PDPageContentStream cs, float x, float y,
                          float w, float h, Color color) throws IOException {
        cs.setNonStrokingColor(color);
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    private void drawRectBorder(PDPageContentStream cs, float x, float y,
                                float w, float h, Color color) throws IOException {
        cs.setStrokingColor(color);
        cs.setLineWidth(0.3f);
        cs.addRect(x, y, w, h);
        cs.stroke();
    }

    private void font(PDPageContentStream cs, Standard14Fonts.FontName name,
                      int size, Color color) throws IOException {
        cs.setFont(new PDType1Font(name), size);
        cs.setNonStrokingColor(color);
    }

    private void text(PDPageContentStream cs, String t, float x, float y) throws IOException {
        if (t == null || t.isEmpty()) return;
        // Replace any characters outside WinAnsi range
        String safe = t.replace("\u20B9", "Rs.")
                       .replace("\u2264", "<=")
                       .replace("\u2013", "-")
                       .replace("\u2014", "--")
                       .replace("\u2022", ".");
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(safe);
        cs.endText();
    }

    private float approximateTextWidth(String s, float size) {
        return s.length() * size * 0.5f;
    }

    // -- Tariff breakdown ---------------------------------------------------

    private TariffBreakdown computeBreakdown(BigDecimal totalLiters, Long apartmentId,
                                             com.aquatrack.smartwaterbilling.entity.BillingCycle cycle) {
        if (totalLiters.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal zero = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            // Return a placeholder breakdown for zero consumption
            return new TariffBreakdown(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(15),
                    BigDecimal.ZERO, BigDecimal.ZERO, zero, zero, zero, BigDecimal.ZERO);
        }

        if (cycle == null || cycle.getCycleEndDate() == null) {
            throw new IllegalArgumentException(
                    "Invoice is missing billing cycle data; cannot compute tiered consumption");
        }

        TariffPlan plan = tariffPlanService.requireActivePlan(apartmentId, cycle.getCycleEndDate());

        BigDecimal usageKl      = totalLiters.divide(LITERS_PER_KL, 6, RoundingMode.HALF_UP);
        BigDecimal tier1LimitKl = plan.getTier1LimitKl();
        BigDecimal tier1Rate    = plan.getTier1Rate();
        BigDecimal tier2Rate    = plan.getTier2Rate();

        BigDecimal tier1Kl, tier2Kl, tier1Charge, tier2Charge;

        if (usageKl.compareTo(tier1LimitKl) <= 0) {
            tier1Kl     = usageKl;
            tier2Kl     = BigDecimal.ZERO;
            tier1Charge = usageKl.multiply(tier1Rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            tier2Charge = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        } else {
            tier1Kl     = tier1LimitKl;
            tier2Kl     = usageKl.subtract(tier1LimitKl);
            tier1Charge = tier1LimitKl.multiply(tier1Rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            tier2Charge = tier2Kl.multiply(tier2Rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal total = tier1Charge.add(tier2Charge).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return new TariffBreakdown(tier1LimitKl, tier1Rate, tier2Rate,
                tier1Kl, tier2Kl, tier1Charge, tier2Charge, total, usageKl);
    }

    /**
     * Immutable value object holding the tiered tariff breakdown for the invoice PDF.
     */
    record TariffBreakdown(
            BigDecimal tier1LimitKl,
            BigDecimal tier1Rate,
            BigDecimal tier2Rate,
            BigDecimal tier1Kl,
            BigDecimal tier2Kl,
            BigDecimal tier1Charge,
            BigDecimal tier2Charge,
            BigDecimal totalCharge,
            BigDecimal usageKl
    ) {}
}
