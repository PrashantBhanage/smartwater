package com.aquatrack.smartwaterbilling.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration.
 * <p>
 * CORS is handled by the {@code CorsConfigurationSource} bean in SecurityConfig,
 * which integrates directly with Spring Security's filter chain. Do NOT add
 * duplicate CORS mappings here — it would cause double CORS headers or conflicts.
 * </p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    // Extend as needed for formatters, interceptors, etc.
}

