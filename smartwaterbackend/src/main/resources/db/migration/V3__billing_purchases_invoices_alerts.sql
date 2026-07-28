-- =============================================================
-- V3__billing_purchases_invoices_alerts.sql
-- Module 2: water purchases, invoices, and in-app alerts.
--
-- Reuses existing Module 1 tables:
--   tariff_plans   (unchanged)
--   billing_cycles (unchanged — OPEN / FINALIZED / ARCHIVED)
-- =============================================================

-- ------------------------------------------------------------
-- 1. WATER PURCHASES (apartment-level bulk water buys per cycle)
-- ------------------------------------------------------------
CREATE TABLE water_purchases (
    id                  BIGSERIAL PRIMARY KEY,
    apartment_id        BIGINT         NOT NULL REFERENCES apartments(id) ON DELETE CASCADE,
    cycle_id            BIGINT         NOT NULL REFERENCES billing_cycles(id) ON DELETE CASCADE,
    volume_purchased_kl NUMERIC(12, 3) NOT NULL CHECK (volume_purchased_kl > 0),
    unit_cost           NUMERIC(12, 4) NOT NULL CHECK (unit_cost >= 0),
    purchase_date       DATE           NOT NULL,
    source              VARCHAR(20)    NOT NULL CHECK (source IN ('TANKER', 'MUNICIPAL')),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_water_purchases_apartment_id ON water_purchases(apartment_id);
CREATE INDEX idx_water_purchases_cycle_id     ON water_purchases(cycle_id);

-- ------------------------------------------------------------
-- 2. INVOICES (one per household per finalized billing cycle)
-- ------------------------------------------------------------
CREATE TABLE invoices (
    id                BIGSERIAL PRIMARY KEY,
    household_id      BIGINT         NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    cycle_id          BIGINT         NOT NULL REFERENCES billing_cycles(id) ON DELETE CASCADE,
    base_charge       NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (base_charge >= 0),
    shared_allocation NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (shared_allocation >= 0),
    adjustments       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total_amount      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    status            VARCHAR(20)    NOT NULL DEFAULT 'ISSUED'
                          CHECK (status IN ('ISSUED', 'PAID', 'CANCELLED')),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_invoice_household_cycle UNIQUE (household_id, cycle_id)
);

CREATE INDEX idx_invoices_household_id ON invoices(household_id);
CREATE INDEX idx_invoices_cycle_id     ON invoices(cycle_id);
CREATE INDEX idx_invoices_status       ON invoices(status);

-- ------------------------------------------------------------
-- 3. ALERTS (in-app records for threshold / leak detections)
-- ------------------------------------------------------------
CREATE TABLE alerts (
    id           BIGSERIAL PRIMARY KEY,
    household_id BIGINT       NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    alert_type   VARCHAR(30)  NOT NULL CHECK (alert_type IN ('THRESHOLD_EXCEEDED', 'LEAK_SUSPECTED')),
    message      TEXT         NOT NULL,
    usage_liters NUMERIC(10, 2),
    reading_date DATE,
    acknowledged BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_alerts_household_id ON alerts(household_id);
CREATE INDEX idx_alerts_alert_type   ON alerts(alert_type);
CREATE INDEX idx_alerts_created_at   ON alerts(created_at);
