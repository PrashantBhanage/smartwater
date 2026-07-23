package com.aquatrack.smartwaterbilling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * SmartWater Billing System — Module 1 entry point.
 * {@code @EnableMethodSecurity} activates {@code @PreAuthorize} on controller methods.
 */
@SpringBootApplication
@EnableMethodSecurity
public class SmartwaterbillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartwaterbillingApplication.class, args);
    }
}
