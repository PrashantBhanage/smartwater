-- Removes legacy camel-case-derived columns that Hibernate created in earlier
-- local databases before TariffPlan had explicit snake_case column mappings.
ALTER TABLE tariff_plans DROP COLUMN IF EXISTS tier1limit_kl;
ALTER TABLE tariff_plans DROP COLUMN IF EXISTS tier1rate;
ALTER TABLE tariff_plans DROP COLUMN IF EXISTS tier2rate;
