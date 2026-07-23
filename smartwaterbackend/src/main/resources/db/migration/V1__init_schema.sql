-- =============================================================
-- V1__init_schema.sql
-- SmartWater Billing System — Initial Database Schema
-- =============================================================

-- ------------------------------------------------------------
-- 1. APARTMENTS
-- ------------------------------------------------------------
CREATE TABLE apartments (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    address           TEXT         NOT NULL,
    total_households  INTEGER      NOT NULL DEFAULT 0 CHECK (total_households >= 0),
    admin_contact     VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ------------------------------------------------------------
-- 2. HOUSEHOLDS
-- ------------------------------------------------------------
CREATE TABLE households (
    id                       BIGSERIAL PRIMARY KEY,
    apartment_id             BIGINT         NOT NULL REFERENCES apartments(id) ON DELETE CASCADE,
    flat_number              VARCHAR(50)    NOT NULL,
    area_sqft                NUMERIC(10, 2),
    occupancy_count          INTEGER        NOT NULL DEFAULT 1 CHECK (occupancy_count >= 0),
    has_meter                BOOLEAN        NOT NULL DEFAULT FALSE,
    daily_threshold_liters   NUMERIC(10, 2) NOT NULL DEFAULT 500.00 CHECK (daily_threshold_liters > 0),
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_apartment_flat UNIQUE (apartment_id, flat_number)
);

-- ------------------------------------------------------------
-- 3. USERS
-- ------------------------------------------------------------
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255),
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'RESIDENT')),
    apartment_id  BIGINT REFERENCES apartments(id) ON DELETE SET NULL,
    household_id  BIGINT REFERENCES households(id) ON DELETE SET NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ------------------------------------------------------------
-- 4. WATER USAGE LOGS
-- ------------------------------------------------------------
CREATE TABLE water_usage_logs (
    id                   BIGSERIAL PRIMARY KEY,
    household_id         BIGINT         NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    reading_date         DATE           NOT NULL,
    meter_reading_value  NUMERIC(15, 3),
    volume_used_liters   NUMERIC(10, 2) NOT NULL CHECK (volume_used_liters >= 0),
    source               VARCHAR(20)    NOT NULL CHECK (source IN ('MANUAL', 'CSV_UPLOAD')),
    usage_status         VARCHAR(10)    NOT NULL CHECK (usage_status IN ('GREEN', 'YELLOW', 'RED')),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_usage_log_household_date UNIQUE (household_id, reading_date)
);

-- ------------------------------------------------------------
-- 5. BILLING CYCLES
-- ------------------------------------------------------------
CREATE TABLE billing_cycles (
    id               BIGSERIAL PRIMARY KEY,
    apartment_id     BIGINT   NOT NULL REFERENCES apartments(id) ON DELETE CASCADE,
    cycle_start_date DATE     NOT NULL,
    cycle_end_date   DATE     NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'FINALIZED', 'ARCHIVED')),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_cycle_dates CHECK (cycle_end_date > cycle_start_date)
);

-- ------------------------------------------------------------
-- 6. TARIFF PLANS
-- ------------------------------------------------------------
CREATE TABLE tariff_plans (
    id                  BIGSERIAL PRIMARY KEY,
    apartment_id        BIGINT         NOT NULL REFERENCES apartments(id) ON DELETE CASCADE,
    tier1_limit_kl      NUMERIC(10, 3) NOT NULL CHECK (tier1_limit_kl > 0),
    tier1_rate          NUMERIC(10, 4) NOT NULL CHECK (tier1_rate >= 0),
    tier2_rate          NUMERIC(10, 4) NOT NULL CHECK (tier2_rate >= 0),
    effective_from_date DATE           NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ------------------------------------------------------------
-- INDEXES
-- ------------------------------------------------------------
CREATE INDEX idx_households_apartment_id      ON households(apartment_id);
CREATE INDEX idx_users_apartment_id           ON users(apartment_id);
CREATE INDEX idx_users_household_id           ON users(household_id);
CREATE INDEX idx_users_email                  ON users(email);
CREATE INDEX idx_usage_logs_household_id      ON water_usage_logs(household_id);
CREATE INDEX idx_usage_logs_reading_date      ON water_usage_logs(reading_date);
CREATE INDEX idx_usage_logs_usage_status      ON water_usage_logs(usage_status);
CREATE INDEX idx_billing_cycles_apartment_id  ON billing_cycles(apartment_id);
CREATE INDEX idx_billing_cycles_status        ON billing_cycles(status);
CREATE INDEX idx_tariff_plans_apartment_id    ON tariff_plans(apartment_id);
