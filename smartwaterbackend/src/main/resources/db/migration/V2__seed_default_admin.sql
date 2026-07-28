-- =============================================================
-- V2__seed_default_admin.sql
-- Seeds one default apartment and one default admin user.
--
-- This migration runs automatically on first startup (fresh DB).
-- On an existing DB that already has data, the WHERE NOT EXISTS
-- guards make both inserts no-ops, so re-applying is safe.
--
-- DEFAULT CREDENTIALS (CHANGE IMMEDIATELY IN PRODUCTION):
--   Email   : admin@smartwater.local
--   Password: SmartWater#2024
--   Role    : ADMIN
--
-- The password hash below is BCrypt(strength=10) of "SmartWater#2024".
-- Generate a new hash with: new BCryptPasswordEncoder().encode("yourpass")
-- =============================================================

-- 1. Default apartment (inserted only when the table is empty)
INSERT INTO apartments (name, address, total_households, admin_contact, created_at, updated_at)
SELECT 'Default Apartment', '1 Setup Lane', 50, 'admin@smartwater.local', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM apartments LIMIT 1);

-- 2. Default admin user (inserted only when this email does not exist)
INSERT INTO users (email, password_hash, full_name, role, apartment_id, created_at, updated_at)
SELECT
    'admin@smartwater.local',
    '$2a$10$BVGy8pJgXn.DxOkmxJTfpexLsAnZ342/O4cik7Mt4QD5utzMe7/oa',
    'Default Admin',
    'ADMIN',
    (SELECT id FROM apartments WHERE admin_contact = 'admin@smartwater.local' LIMIT 1),
    NOW(),
    NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@smartwater.local');
