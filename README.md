# SmartWater — Smart Water Usage & Consumer Billing System

An IoT-oriented, full-stack platform for apartment communities to track water usage, split shared water costs fairly across households, and alert residents about overuse or possible leaks.

Built as part of an 8-week internship project (Springboard). This repo is a monorepo containing both backend and frontend.

## Tech Stack

**Backend**
- Java 21, Spring Boot 3.4.3
- Spring Data JPA + PostgreSQL
- Flyway (database migrations)
- Spring Security 6 with JWT authentication
- springdoc-openapi (Swagger UI)
- Jakarta Bean Validation
- Maven
- JUnit 5 + Mockito, Spring Boot Test (H2 in-memory for CI/tests)

**Frontend**
- React.js

## Project Status

**Module 1 of 4 — Complete** ✅
Schema, authentication, core APIs, water usage logging (manual + CSV bulk upload), colour-coded usage alerts, and automated tests are done.

**Modules 2–4 — Not started yet**

| Module | Status | Covers |
|---|---|---|
| 1. Schema, Usage Logging & Core APIs | ✅ Done | DB schema, JWT auth, apartment/household CRUD, usage logging, CSV upload, colour-coding |
| 2. Billing Engine, Distribution & Alerts | ⬜ Pending | Tiered tariff billing, shared-cost splitting, leak/overuse alert notifications |
| 3. React Dashboard, Admin Panel & Invoices | ⬜ Pending | Resident dashboard, admin panel, PDF invoice generation |
| 4. Integration, Testing & Finalization | ⬜ Pending | End-to-end testing, load testing, documentation, final demo |

## Repository Structure

```
smartwater/
├── smartwaterbackend/     # Spring Boot API
├── smartwaterfrontend/    # React app (scaffolded, not yet built out)
├── .gitignore
└── commit_each_file.sh    # (dev utility script — safe to ignore/remove)
```

## Backend Setup

### Prerequisites
- Java 21 (JDK)
- Maven (or use the included `./mvnw` wrapper)
- PostgreSQL running locally (v14+ recommended)

### 1. Clone the repo
```bash
git clone https://github.com/PrashantBhanage/smartwater.git
cd smartwater/smartwaterbackend
```

### 2. Create the database
```bash
psql -U postgres
```
Inside the `psql` prompt:
```sql
CREATE DATABASE water_billing_db;
\q
```

### 3. Configure local credentials
Create `src/main/resources/application-local.properties` (this file is gitignored and must be created manually — it does **not** come from the repo):
```properties
DB_URL=jdbc:postgresql://localhost:5432/water_billing_db
DB_USERNAME=postgres
DB_PASSWORD=your_postgres_password
JWT_SECRET=replace_with_a_random_32+_character_secret
```

### 4. Run the app
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```
Flyway will automatically create all tables on first startup.

The app runs at: `http://localhost:8080`

### 5. Explore the API via Swagger
Open: `http://localhost:8080/swagger-ui/index.html`

Flow to test:
1. `POST /api/auth/register` — register a user
2. `POST /api/auth/login` — get a JWT token
3. Click **Authorize** in Swagger UI, paste the token
4. Try any protected endpoint (e.g. create an apartment, log water usage)

### 6. Run tests
```bash
mvn test
```
Tests use H2 in PostgreSQL-compatibility mode — no external database or Docker required.

## Known Setup Gotcha

There's currently a bootstrapping issue: creating the first `ADMIN` user requires an `apartmentId`, but apartments can only be created by an `ADMIN`. Until this is fixed, bootstrap manually via a direct SQL insert into the `apartments` table before registering your first admin user, e.g.:
```sql
INSERT INTO apartments (name, address, total_households, admin_contact, created_at)
VALUES ('Test Apartment', 'Test Address', 10, 'admin@example.com', now());
```
Then register your admin user with `apartmentId: 1`.

## Frontend Setup

The `smartwaterfrontend` folder is currently a scaffold and not yet connected to the backend (planned for Module 3).

```bash
cd smartwaterfrontend
npm install
npm start
```

## Database Schema (Module 1)

Six core tables, managed via Flyway migrations in `src/main/resources/db/migration/`:

| Table | Purpose |
|---|---|
| `apartments` | Apartment complex details |
| `households` | Individual flats within an apartment, including a `daily_threshold_liters` field used for usage colour-coding |
| `users` | Login credentials; role is `ADMIN` or `RESIDENT` |
| `water_usage_logs` | Per-household usage entries with a stored `usage_status` (GREEN/YELLOW/RED) |
| `billing_cycles` | Billing period tracking (OPEN/FINALIZED/ARCHIVED) |
| `tariff_plans` | Two-tier water pricing per apartment |

## Usage Colour-Coding

Each usage log is tagged at creation time based on a fixed daily threshold per household (default 500L/day):

```
GREEN  → usage ≤ threshold
YELLOW → threshold < usage < 1.5 × threshold
RED    → usage ≥ 1.5 × threshold
```

This value is stored, not recalculated later, so historical data stays accurate even if thresholds change.

## API Overview

| Endpoint | Auth | Description |
|---|---|---|
| `POST /api/auth/register` | Public | Register a user |
| `POST /api/auth/login` | Public | Get a JWT |
| `GET /api/auth/me` | Any authenticated user | View own profile |
| `PATCH /api/auth/me` | Any authenticated user | Update own profile |
| `POST /api/apartments` | ADMIN | Onboard an apartment |
| `GET /api/apartments/{id}` | Authenticated | View apartment |
| `POST /api/households` | ADMIN | Register a household |
| `GET /api/households/{id}` | Authenticated | View household |
| `POST /api/households/{id}/assign-resident` | ADMIN | Link a resident to a household |
| `PATCH /api/households/{id}/meter-config` | ADMIN | Update meter/threshold config |
| `POST /api/usage-logs` | Authenticated | Log a manual water usage reading |
| `GET /api/usage-logs?householdId=` | Authenticated | View usage logs |
| `POST /api/usage-logs/bulk-upload` | ADMIN | Upload a CSV of usage readings |

Full interactive docs available via Swagger UI once the app is running.

## Roadmap

- [ ] Fix admin/apartment bootstrap chicken-and-egg problem
- [ ] Module 2: Tiered billing engine, shared-cost distribution, leak alerts
- [ ] Module 3: React dashboard, admin panel, PDF invoices
- [ ] Module 4: End-to-end testing, load testing, final docs

