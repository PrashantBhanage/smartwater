-- =============================================================
-- seed_test_admin.sql
-- Loaded by @Sql in integration tests to bootstrap a known
-- apartment + admin + household + resident so every IT class
-- can get a real JWT without a chicken-and-egg problem.
--
-- Test credentials:
--   Admin  : it-admin@test.com  / TestPass#1
--   Resident: it-resident@test.com / TestPass#1
-- =============================================================

-- Clean slate (order matters due to FKs — Module 2 tables first)
DELETE FROM alerts;
DELETE FROM invoices;
DELETE FROM water_purchases;
DELETE FROM water_usage_logs;
DELETE FROM users;
DELETE FROM households;
DELETE FROM billing_cycles;
DELETE FROM tariff_plans;
DELETE FROM apartments;

-- 1. Seed apartment
INSERT INTO apartments (id, name, address, total_households, admin_contact, created_at, updated_at)
VALUES (100, 'IT Test Towers', '1 Integration Ave', 20, 'it-admin@test.com', NOW(), NOW());

-- 2. Seed household (used for resident registration and usage log tests)
INSERT INTO households (id, apartment_id, flat_number, occupancy_count, has_meter, daily_threshold_liters, created_at, updated_at)
VALUES (100, 100, 'A-101', 3, TRUE, 500.00, NOW(), NOW());

-- 3. Seed admin user
--    BCrypt(strength=10) of "TestPass#1"
INSERT INTO users (id, email, password_hash, full_name, role, apartment_id, household_id, created_at, updated_at)
VALUES (100, 'it-admin@test.com',
        '$2a$10$633/oTnt8Wn4WnS/ubZt8unR1N3a/IkSXQa8v1NeR767nrUnPDUmW',
        'IT Admin', 'ADMIN', 100, NULL, NOW(), NOW());

-- 4. Seed resident user (already linked to household 100)
INSERT INTO users (id, email, password_hash, full_name, role, apartment_id, household_id, created_at, updated_at)
VALUES (101, 'it-resident@test.com',
        '$2a$10$633/oTnt8Wn4WnS/ubZt8unR1N3a/IkSXQa8v1NeR767nrUnPDUmW',
        'IT Resident', 'RESIDENT', NULL, 100, NOW(), NOW());
