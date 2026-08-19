package com.aquatrack.smartwaterbilling.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends alert notifications. Integrates with {@link EmailNotificationService} for
 * asynchronous transactional email delivery. Falls back to log output when mail is disabled.
 */
@Service
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;
    private final EmailNotificationService emailNotificationService;
    private final boolean mailEnabled;
    private final String fromAddress;

    public NotificationService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Autowired(required = false) EmailNotificationService emailNotificationService,
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @Value("${app.mail.from:noreply@smartwater.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.emailNotificationService = emailNotificationService;
        this.mailEnabled = mailEnabled && mailSender != null;
        this.fromAddress = fromAddress;
    }

    public void notifyAlert(String toEmail, String subject, String body) {
        if (emailNotificationService != null) {
            emailNotificationService.sendRawAlert(toEmail, subject, body);
            return;
        }

        if (mailEnabled) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(toEmail);
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
                log.info("Alert email sent to {}: {}", toEmail, subject);
                return;
            } catch (Exception ex) {
                log.warn("Failed to send alert email to {} — falling back to log: {}",
                        toEmail, ex.getMessage());
            }
        }
        log.warn("[ALERT STUB] to={} | {} | {}", toEmail, subject, body);
    }
}
