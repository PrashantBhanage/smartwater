# Implementation Plan

## [Overview]

Complete Milestone 2 of SmartWater — the billing engine, cost distribution, and alert system — by closing Section 1 gaps (composite indexes + validation annotations) and verifying the entire uncommitted Milestone 2 WIP passes the backend test suite.

The working tree at `/home/prrssshhhh/smartwater/smartwaterbackend` already contains a substantial, uncommitted Milestone 2 implementation: the `V5__add_billing_module.sql` Flyway migration, JPA entities `BulkWaterPurchase` and `BillingCycleInvoice`, repositories, services (`BulkWaterPurchaseService`, `InvoiceGenerationService`, `TariffCalculationService`, `CostDistributionService`), controllers (`BillingController`, `BulkWaterPurchaseController`), DTOs, and integration/unit tests (`Milestone2BillingControllerIT`, `CostDistributionServiceMilestone2Test`, `TariffCalculationServiceTest`). The plan is to (1) fix the two explicit Section 1 deviations — the migration uses single-column indexes instead of the requested composite `(apartment_id, purchase_date)` and `(household_id, billing_cycle_id)` indexes, and the entities lack Jakarta validation annotations — and (2) run the full `mvn test` suite, fixing any compilation or test failures that surface across the WIP.

The implementation fits into the existing Spring Boot 3.4.3 / Java 17 / PostgreSQL / Flyway / H2-test architecture. The new billing module coexists with the legacy Module 1/2 `Invoice`/`WaterPurchase` path (`BillingCycleService.finalizeCycle` → `Invoice` table) and the new `BillingCycleInvoice`/`BulkWaterPurchase` path (`InvoiceGenerationService.finalizeCycle` → `billing_cycle_invoices` table). Both paths are wired to separate controllers (`BillingCycleController` vs `BillingController`), so they can coexist, but the plan must verify no test relies on the wrong path and that the new path's integration test passes.

## [Types]

Add Jakarta Bean Validation annotations to the two new JPA entities and correct the migration's index definitions.

**`BulkWaterPurchase` entity** (`smartwaterbackend/src/main/java/com/aquatrack/smartwaterbilling/entity/BulkWaterPurchase.java`):
- Add `@NotNull` on `purchaseDate`, `volumeLiters`, `unitCost`, and `apartment`.
- Add `@DecimalMin(value = "0.0", inclusive = true)` on `volumeLiters` and `unitCost`.
- Add `@PositiveOrZero` on `volumeLiters` and `unitCost` (redundant with `@DecimalMin` but explicit).
- Keep existing `@Column` precision/scale mappings and `@PrePersist`/`@PreUpdate` lifecycle hooks.

**`BillingCycleInvoice` entity** (`smartwaterbackend/src/main/java/com/aquatrack/smartwaterbilling/entity/BillingCycleInvoice.java`):
- Add `@NotNull` on `household`, `billingCycle`.
- Add `@DecimalMin(value = "0.0", inclusive = true)` on `baseCharge`, `sharedCostAllocation`, `totalCharge`.
- Add `@NotBlank` on `paidStatus` (defaults to `"UNPAID"`).
- Keep existing `@Builder.Default` defaults, `@PrePersist` hook, and `recomputeTotal()`.

**`V5__add_billing_module.sql`** (`smartwaterbackend/src/main/resources/db/migration/V5__add_billing_module.sql`):
- Replace the four single-column indexes with two composite indexes:
  - `CREATE INDEX idx_bulk_water_purchases_apartment_date ON bulk_water_purchases(apartment_id, purchase_date);`
  - `CREATE INDEX idx_billing_cycle_invoices_household_cycle ON billing_cycle_invoices(household_id, billing_cycle_id);`
- Keep the existing `paid_status` index on `billing_cycle_invoices` (useful for filtering unpaid invoices).

## [Files]

Modify three existing files; no new files are required.

- **`smartwaterbackend/src/main/resources/db/migration/V5__add_billing_module.sql`** — Replace the four single-column index statements with the two composite indexes described in [Types]. No table/column changes.
- **`smartwaterbackend/src/main/java/com/aquatrack/smartwaterbilling/entity/BulkWaterPurchase.java`** — Add `jakarta.validation.constraints.*` imports and the validation annotations described in [Types].
- **`smartwaterbackend/src/main/java/com/aquatrack/smartwaterbilling/entity/BillingCycleInvoice.java`** — Add `jakarta.validation.constraints.*` imports and the validation annotations described in [Types].

No files are deleted or moved. No configuration files change.

## [Functions]

No new functions are added. The following existing functions are modified only by adding annotations (no signature or logic changes):

- **`BulkWaterPurchase`** — no method changes; annotations only.
- **`BillingCycleInvoice`** — no method changes; annotations only.

The verification step will exercise the following existing functions (no changes expected unless tests fail):

- `BulkWaterPurchaseService.createPurchase(Long, BulkPurchaseRequest)` — creates a bulk purchase.
- `BulkWaterPurchaseService.getPurchases(Long, LocalDate, LocalDate)` — lists purchases with totals.
- `InvoiceGenerationService.finalizeCycle(BillingCycle)` — generates `BillingCycleInvoice` rows, distributes shared cost, finalizes the cycle, and triggers `AlertService.scanForLeaks()`.
- `CostDistributionService.distributeApartmentCost(Apartment, BillingCycle, BigDecimal)` — proportional-by-usage, fallback-by-area, fallback-equal distribution.
- `TariffCalculationService.calculateHouseholdBill(Household, BillingCycle)` — two-tier tariff base charge.
- `AlertService.checkThreshold(Household, BigDecimal, LocalDate)` — threshold alert on usage-log creation.
- `BillingController.finalizeCycle(Long)` / `getInvoices(Long, User)` / `getMyInvoices(User)` — REST endpoints.
- `BulkWaterPurchaseController.createPurchase(Long, BulkPurchaseRequest)` / `getPurchases(...)` — REST endpoints.

## [Classes]

No new classes are added. Two existing classes are modified with validation annotations only:

- **`BulkWaterPurchase`** (`.../entity/BulkWaterPurchase.java`) — add `@NotNull`/`@DecimalMin`/`@PositiveOrZero` on fields; no structural change.
- **`BillingCycleInvoice`** (`.../entity/BillingCycleInvoice.java`) — add `@NotNull`/`@DecimalMin`/`@NotBlank` on fields; no structural change.

The following existing classes are exercised by the verification step but are not modified unless a test failure requires it:

- `BulkWaterPurchaseService`, `InvoiceGenerationService`, `CostDistributionService`, `TariffCalculationService`, `AlertService`, `BillingController`, `BulkWaterPurchaseController`, `BillingCycleService` (legacy path — untouched).

## [Dependencies]

No new dependencies. The existing `spring-boot-starter-validation` (already in `pom.xml`) provides `jakarta.validation.constraints.*`. The test stack (`spring-boot-starter-test`, `spring-security-test`, H2, Testcontainers) is already configured.

## [Testing]

Run the full backend test suite with `mvn test` from `smartwaterbackend/` and fix any failures.

The relevant existing tests are:

- `src/test/java/com/aquatrack/smartwaterbilling/integration/Milestone2BillingControllerIT.java` — end-to-end flow: create tariff plan → open cycle → bulk purchase → log usage → finalize → admin/resident invoice queries → alert queries.
- `src/test/java/com/aquatrack/smartwaterbilling/service/CostDistributionServiceMilestone2Test.java` — unit tests for `distributeApartmentCost` (usage-proportional, area-fallback, equal-fallback).
- `src/test/java/com/aquatrack/smartwaterbilling/service/TariffCalculationServiceTest.java` — unit tests for the two-tier tariff calculation.
- `src/test/java/com/aquatrack/smartwaterbilling/service/AlertServiceTest.java` — threshold/leak detection unit tests.
- `src/test/java/com/aquatrack/smartwaterbilling/integration/BillingModuleIT.java` — legacy billing path tests (must still pass).
- All other existing `*Test`/`*IT` classes (Auth, Apartment, Household, UsageLog, Alert) — must still pass.

Validation strategy: after adding annotations, run `mvn test` and confirm all tests pass. If the H2 PostgreSQL-compatibility mode chokes on the composite index syntax, adjust the migration to use H2-compatible syntax (e.g., `CREATE INDEX ... ON table(col1, col2)` is standard SQL and should work in both H2 and PostgreSQL).

## [Implementation Order]

1. Update `V5__add_billing_module.sql` — replace single-column indexes with the two composite indexes.
2. Add validation annotations to `BulkWaterPurchase.java`.
3. Add validation annotations to `BillingCycleInvoice.java`.
4. Run `cd smartwaterbackend && mvn test` and capture results.
5. Fix any compilation or test failures that surface (e.g., H2 index syntax, entity mapping issues, controller wiring conflicts between the legacy `BillingCycleService` path and the new `InvoiceGenerationService` path).
6. **IMPORTANT — Controller wiring conflict:** If resolving a controller wiring conflict requires choosing between the legacy `BillingCycleService` and the new `InvoiceGenerationService`, **STOP and ask the user which one to keep and why** before making any change. Do not unilaterally pick one.
7. Re-run `mvn test` until green.
