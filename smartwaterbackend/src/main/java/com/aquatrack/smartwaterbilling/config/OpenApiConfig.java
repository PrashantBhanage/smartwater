package com.aquatrack.smartwaterbilling.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc / OpenAPI 3 configuration.
 *
 * <ul>
 *   <li>Registers a global HTTP Bearer (JWT) security scheme so that Swagger UI
 *       displays an <strong>Authorize</strong> button.</li>
 *   <li>Applies the scheme globally, meaning every endpoint will show a lock icon
 *       and will include the {@code Authorization: Bearer <token>} header once a
 *       token is pasted in the Authorize dialog.</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI smartWaterOpenAPI() {
        return new OpenAPI()
                // ── Security scheme definition ─────────────────────────────────
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the JWT token obtained from POST /api/auth/login. "
                                        + "It will be sent as: Authorization: Bearer <token>")))
                // ── Apply scheme globally to all operations ────────────────────
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                // ── API metadata ───────────────────────────────────────────────
                .info(new Info()
                        .title("SmartWater Billing API")
                        .version("1.0.0")
                        .description("REST API for the SmartWater Apartment Water Usage and Billing System. "
                                + "All protected endpoints require a JWT Bearer token — "
                                + "use the **Authorize** button above to set it.")
                        .contact(new Contact()
                                .name("AquaTrack Team")
                                .email("support@aquatrack.com"))
                        .license(new License()
                                .name("Private — All rights reserved")));
    }
}
