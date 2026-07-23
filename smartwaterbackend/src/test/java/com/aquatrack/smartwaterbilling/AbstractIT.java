package com.aquatrack.smartwaterbilling;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for all integration tests.
 * <p>
 * Uses H2 in PostgreSQL-compatibility mode (configured in application-test.properties)
 * so no Docker/Testcontainers is required. Flyway migrations run automatically
 * on startup using H2's PostgreSQL compatibility layer.
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIT {

    @Autowired
    protected MockMvc mockMvc;
}
