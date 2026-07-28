package com.aquatrack.smartwaterbilling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * SmartWater Billing System entry point.
 * {@code @EnableMethodSecurity} activates {@code @PreAuthorize} on controller methods.
 * {@code @EnableScheduling} activates the Module 2 leak-detection cron job.
 */
@SpringBootApplication
@EnableMethodSecurity
@EnableScheduling
public class SmartwaterbillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartwaterbillingApplication.class, args);
    }
}
