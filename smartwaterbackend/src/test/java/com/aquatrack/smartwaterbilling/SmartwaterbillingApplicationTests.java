package com.aquatrack.smartwaterbilling;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test — verifies the Spring context loads successfully with H2 PostgreSQL mode.
 */
@ActiveProfiles("test")
class SmartwaterbillingApplicationTests extends AbstractIT {

    @Test
    void contextLoads() {
        // If this passes, Spring context (Flyway, JPA, Security) loaded cleanly with H2.
    }
}
