-- =============================================================
-- V5__add_billing_module.sql
-- Milestone 2 database schema
-- =============================================================

-- 1. BULK WATER PURCHASES
CREATE TABLE bulk_water_purchases (
    id            BIGSERIAL PRIMARY KEY,
    apartment_id  BIGINT         NOT NULL REFERENCES apartments(id) ON DELETE CASCADE,
    purchase_date DATE           NOT NULL,
    volume_liters NUMERIC(12, 2) NOT NULL CHECK (volume_liters >= 0),
    unit_cost     NUMERIC(12, 4) NOT NULL CHECK (unit_cost >= 0),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bulk_water_purchases_apartment_date ON bulk_water_purchases(apartment_id, purchase_date);

-- 2. BILLING CYCLE INVOICES
CREATE TABLE billing_cycle_invoices (
    id                     BIGSERIAL PRIMARY KEY,
    household_id           BIGINT         NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    billing_cycle_id       BIGINT         NOT NULL REFERENCES billing_cycles(id) ON DELETE CASCADE,
    base_charge            NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (base_charge >= 0),
    shared_cost_allocation NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (shared_cost_allocation >= 0),
    total_charge           NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (total_charge >= 0),
    paid_status            VARCHAR(20)    NOT NULL DEFAULT 'UNPAID' CHECK (paid_status IN ('PAID', 'UNPAID')),
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_billing_cycle_invoice UNIQUE (household_id, billing_cycle_id)
);

CREATE INDEX idx_billing_cycle_invoices_household_cycle ON billing_cycle_invoices(household_id, billing_cycle_id);
CREATE INDEX idx_billing_cycle_invoices_paid_status ON billing_cycle_invoices(paid_status);
