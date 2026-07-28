package com.aquatrack.smartwaterbilling;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for all integration tests.
 *
 * <h2>Database strategy</h2>
 * Uses H2 in PostgreSQL-compatibility mode (configured in application-test.properties).
 * Flyway migrations run automatically on context startup.
 *
 * <h2>State isolation</h2>
 * {@code @DirtiesContext(classMode = BEFORE_CLASS)} tears down and rebuilds the
 * Spring context before every IT class so that state inserted by one test class
 * cannot bleed into another.
 *
 * <h2>Seed data</h2>
 * {@code @Sql("seed_test_admin.sql")} inserts a known apartment, household,
 * admin user, and resident user before each test <em>method</em>.
 * Test credentials:
 * <ul>
 *   <li>Admin    : {@code it-admin@test.com}    / {@code TestPass#1}</li>
 *   <li>Resident : {@code it-resident@test.com} / {@code TestPass#1}</li>
 *   <li>Apartment ID: 100 | Household ID: 100</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Sql(scripts = "/sql/seed_test_admin.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public abstract class AbstractIT {

    @Autowired
    protected MockMvc mockMvc;

    // Seeded constants — use these in subclasses to avoid magic literals
    protected static final Long   SEEDED_APARTMENT_ID   = 100L;
    protected static final Long   SEEDED_HOUSEHOLD_ID   = 100L;
    protected static final Long   SEEDED_ADMIN_USER_ID  = 100L;
    protected static final String SEEDED_ADMIN_EMAIL    = "it-admin@test.com";
    protected static final String SEEDED_ADMIN_PASSWORD = "TestPass#1";
    protected static final String SEEDED_RESIDENT_EMAIL    = "it-resident@test.com";
    protected static final String SEEDED_RESIDENT_PASSWORD = "TestPass#1";
    protected static final String SEEDED_FLAT_NUMBER    = "A-101";
}
