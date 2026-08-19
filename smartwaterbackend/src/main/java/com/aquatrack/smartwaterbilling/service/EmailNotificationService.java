package com.aquatrack.smartwaterbilling.service;

import com.aquatrack.smartwaterbilling.entity.Household;
import com.aquatrack.smartwaterbilling.entity.Invoice;
import com.aquatrack.smartwaterbilling.entity.User;
import com.aquatrack.smartwaterbilling.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for sending asynchronous transactional emails for billing summaries,
 * usage threshold overuse warnings, and leak/anomaly detection alerts.
 */
@Service
@Slf4j
public class EmailNotificationService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final boolean mailEnabled;
    private final String fromAddress;
    private final String baseUrl;

    public EmailNotificationService(
            @Autowired(required = false) JavaMailSender mailSender,
            UserRepository userRepository,
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @Value("${app.mail.from:noreply@smartwater.local}") String fromAddress,
            @Value("${app.mail.base-url:http://localhost:8080}") String baseUrl) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.mailEnabled = mailEnabled && mailSender != null;
        this.fromAddress = fromAddress;
        this.baseUrl = baseUrl;
    }

    /**
     * Sends a monthly bill summary alert with invoice details and PDF download link upon billing cycle finalization.
     */
    @Async("emailTaskExecutor")
    public void sendBillingAlert(Invoice invoice) {
        if (invoice == null) return;

        Household household = invoice.getHousehold();
        String recipient = resolveRecipient(household);
        String downloadUrl = baseUrl + "/api/invoices/" + invoice.getId() + "/download";

        String subject = "[SmartWater] Monthly Water Bill Issued - Flat " + household.getFlatNumber();
        String body = String.format("""
                Dear Resident,

                Your monthly water bill for %s (Flat %s) has been generated.

                BILL SUMMARY:
                --------------------------------------------------
                Invoice ID        : %d
                Billing Period    : %s to %s
                Base Charge       : Rs. %.2f
                Shared Allocation : Rs. %.2f
                Adjustments       : Rs. %.2f
                --------------------------------------------------
                TOTAL DUE         : Rs. %.2f
                Status            : %s

                You can download your PDF invoice here:
                %s

                Please pay the total due within 30 days.

                Best regards,
                AquaTrack Smart Water Management
                """,
                household.getApartment() != null ? household.getApartment().getName() : "Apartment",
                household.getFlatNumber(),
                invoice.getId(),
                invoice.getBillingCycle().getCycleStartDate().format(DATE_FMT),
                invoice.getBillingCycle().getCycleEndDate().format(DATE_FMT),
                invoice.getBaseCharge(),
                invoice.getSharedAllocation(),
                invoice.getAdjustments(),
                invoice.getTotalAmount(),
                invoice.getStatus(),
                downloadUrl
        );

        sendEmail(recipient, subject, body);
    }

    /**
     * Sends an overuse warning email when daily or monthly usage crosses configured threshold.
     */
    @Async("emailTaskExecutor")
    public void sendOveruseWarning(Household household, BigDecimal volumeLiters, LocalDate readingDate) {
        if (household == null) return;

        String recipient = resolveRecipient(household);
        String subject = "[SmartWater Warning] Water Usage Threshold Exceeded - Flat " + household.getFlatNumber();

        String body = String.format("""
                Dear Resident,

                This is an automated alert from AquaTrack. Your water usage on %s reached %.2f Liters, which exceeds your configured daily threshold of %.2f Liters.

                WATER SAVING TIPS:
                --------------------------------------------------
                1. Check all taps and toilets for minor leaks or slow drips.
                2. Install low-flow aerators on kitchen and bathroom faucets.
                3. Turn off the tap while brushing teeth or soaping dishes.
                4. Run full loads in washing machines and dishwashers.
                --------------------------------------------------

                Tracking your water consumption helps keep your monthly bill low and conserves community resources.

                Best regards,
                AquaTrack Smart Water Management
                """,
                readingDate.format(DATE_FMT),
                volumeLiters,
                household.getDailyThresholdLiters() != null ? household.getDailyThresholdLiters() : BigDecimal.ZERO
        );

        sendEmail(recipient, subject, body);
    }

    /**
     * Sends an anomaly/leak alert when statistical outlier detection (usage > 2σ above average) flags a potential leak.
     * Sent to household resident and apartment admins.
     */
    @Async("emailTaskExecutor")
    public void sendLeakAlert(Household household, BigDecimal volumeLiters, LocalDate readingDate) {
        if (household == null) return;

        List<String> recipients = resolveRecipientsForLeak(household);
        String subject = "[SmartWater URGENT] Suspected Water Leak Detected - Flat " + household.getFlatNumber();

        String body = String.format("""
                URGENT NOTICE: Suspected Water Leak Detected

                Apartment Complex : %s
                Flat / Unit       : %s
                Reading Date      : %s
                Detected Volume   : %.2f Liters (> 2 std. dev. above 30-day average)

                RECOMMENDED ACTIONS:
                --------------------------------------------------
                - Inspect main valves, toilet flush tanks, and plumbing fixtures immediately.
                - If you are away, notify apartment maintenance to shut off the unit supply valve.
                --------------------------------------------------

                This notification has been dispatched to both the resident and apartment management.

                Best regards,
                AquaTrack Smart Water Management
                """,
                household.getApartment() != null ? household.getApartment().getName() : "Apartment",
                household.getFlatNumber(),
                readingDate.format(DATE_FMT),
                volumeLiters
        );

        for (String recipient : recipients) {
            sendEmail(recipient, subject, body);
        }
    }

    /**
     * Sends a raw email alert (generic helper for NotificationService).
     */
    @Async("emailTaskExecutor")
    public void sendRawAlert(String toEmail, String subject, String body) {
        sendEmail(toEmail, subject, body);
    }

    private void sendEmail(String toEmail, String subject, String body) {
        if (mailEnabled) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(toEmail);
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
                log.info("Transactional email sent to {}: {}", toEmail, subject);
                return;
            } catch (Exception ex) {
                log.warn("Failed to send transactional email to {} — falling back to log: {}", toEmail, ex.getMessage());
            }
        }
        log.info("[EMAIL STUB] to={} | subject={} |\n{}", toEmail, subject, body);
    }

    private String resolveRecipient(Household household) {
        return userRepository.findFirstByHouseholdId(household.getId())
                .map(User::getEmail)
                .orElseGet(() -> household.getApartment() != null
                        ? household.getApartment().getAdminContact()
                        : "admin@smartwater.local");
    }

    private List<String> resolveRecipientsForLeak(Household household) {
        List<String> list = new ArrayList<>();
        userRepository.findFirstByHouseholdId(household.getId())
                .ifPresent(u -> list.add(u.getEmail()));

        if (household.getApartment() != null && household.getApartment().getAdminContact() != null) {
            String adminContact = household.getApartment().getAdminContact();
            if (!list.contains(adminContact)) {
                list.add(adminContact);
            }
        }

        if (list.isEmpty()) {
            list.add("admin@smartwater.local");
        }
        return list;
    }
}
