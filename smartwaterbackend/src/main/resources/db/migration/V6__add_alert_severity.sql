-- =============================================================
-- V6__add_alert_severity.sql
-- Adds severity column to alerts table (Milestone 2 alert engine).
-- V3 is already applied in production DBs, so we ALTER rather than edit it.
-- =============================================================

ALTER TABLE alerts
    ADD COLUMN severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM'
    CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));