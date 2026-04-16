# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

EliSmart LIMS: a Laboratory Information Management System featuring hierarchical Protocols (defining curve type, recovery/CV limits, and required reagents via brake tables) and Experiments. It tracks MeasurementPairs (replicates) with raw signals, mean, %CV, and %Recovery, linked to specific Reagent Batches for full lot-traceability. The system includes an automated validation engine, 4PL curve fitting, CSV instrument import, and AI-powered analysis via Google Gemini.

## Tech Stack

- Backend: Java 21, Spring Boot 3.4.1 (Maven)
- Frontend: Python 3.14.3, Streamlit (multipage app with session-based auth gate)
- Database: H2 Database (Embedded mode, file-based persistence)
- Migrations: Flyway (schema management via `src/main/resources/db/migration/`)
- AI: Google Gemini API via LangChain4j (`dev.langchain4j:langchain4j-google-ai-gemini`), configured in `config/GeminiConfig.java`
- Security: Spring Security + JWT (per-user authentication and RBAC)
- Tunnel/Deploy: Pinggy (for remote access)

## Environment Setup

**Backend** environment variables (set in shell or via `.env` file sourced before startup):
- `JWT_SECRET` — secret key for JWT token signing (**required**, min 32 chars; app refuses to start if missing/short)
- `ADMIN_PASSWORD` — password for the `admin` user seeded on first boot (optional; random 16-char password generated if absent)
- `GEMINI_API_KEY` — Google Gemini API key (required for `/api/ai/analyze`; other endpoints work without it)
- `GEMINI_MODEL` — override Gemini model name (optional, defaults to `gemini-2.0-flash`)
- `JWT_EXPIRATION_MS` — token expiration in milliseconds (optional, defaults to `86400000` = 24 h)
- `CORS_ALLOWED_ORIGIN` — allowed CORS origin (optional, defaults to `http://localhost:8501`; **required for non-local deployments**)

See `.env.example` for the full list of variables and expected formats.

**Frontend** credentials live in `frontend/.streamlit/secrets.toml` (never committed). It contains `backend_url`. User credentials are now managed via the backend authentication system — the frontend sends username/password to `/api/auth/login` and stores the JWT token in session state.

## Development Conventions

- **Naming**: Java: PascalCase for classes, camelCase for variables and methods. Python: snake_case for scripts and functions. Database: snake_case for table and column naming (managed via Flyway migrations).
- **Architecture**: Standard Spring Boot Layered Architecture: Controller → Service → Repository → Entity.
- **Single Responsibility Principle**: Each service owns exactly one domain. If TrialService needs to read experiment data, it calls ExperimentService methods — it never injects ExperimentRepository directly. Only the owning service injects its repository.
- **DTO pattern**: Request DTOs are plain Java records (no Lombok). Response DTOs are Java records annotated with Lombok `@Builder` — no manual builder inner class. Call them as `ResponseDTO.builder().fieldName(val).build()` (no `withXxx` prefix).
- **DTO mapping via Mappers**: All DTO ↔ Entity construction and response mapping lives in dedicated Mapper classes under `mapper/`. Service orchestration methods must not contain builder() chains or `new` ResponseDTO constructor calls. Mappers use `ResponseDTO.builder().fieldName(val).build()`. Mapper classes must be static utility classes: all methods are `static`, the class is `final`, the private constructor throws `UnsupportedOperationException`. Mappers are never injected via `@Component` — call them as `MapperName.method()`.
- **Service mapping**: Every service public method returns DTOs (Response), never entities. Controllers call service methods and wrap results in `ResponseEntity`. All mapping (entity→DTO, DTO→entity) happens in the service layer via Mapper classes.
- **Audit trail**: All entities extend `Auditable` (`@MappedSuperclass`), which provides `createdAt`, `updatedAt`, `createdBy`, and `updatedBy` columns populated automatically by Spring Data JPA auditing via `AuditorAwareImpl` (reads principal from `SecurityContextHolder`). Never set these fields manually. All field-level changes are logged to the `audit_log` table via `AuditLogService`.
- **Change history**: Every PUT/PATCH that modifies a persisted field must produce an `AuditLog` entry containing: entity type, entity ID, field name, old value, new value, changed by (username from security context), changed at (timestamp), and reason (optional, required for status overrides and outlier flags). `AuditLogService.logChange()` is the single entry point — never write to `audit_log` directly.
- **Data Transfer**: Strict use of DTOs for communication between Backend and Frontend.
- **Boilerplate**: Use Lombok (@Getter, @Setter, @Builder, @NoArgsConstructor, @AllArgsConstructor) to keep code clean. Do NOT use @Data on JPA entities — use @Getter @Setter and implement equals/hashCode based on `id` only.
- **Commit Messages**: Follow Conventional Commits (e.g., feat: implement excel parsing, fix: h2 connection string).
- **Language**: Code, Javadoc comments, and technical documentation must be in English. User-facing AI insights and logs may be in Italian if requested by the end-user.
- **Javadoc**: Every new or modified class, method, and field must include Javadoc. Update existing Javadoc when changing signatures or behavior. Public and protected members are mandatory; package-private and private are encouraged for non-trivial logic.
- **Pre-commit**: Before every commit, scan all untracked files against `.gitignore` for credentials, generated artifacts, IDE configs, and build output. Add missing patterns if any are found.
- **Server-side derived values**: All derived metrics (signalMean, cvPct, recoveryPct) must be calculated server-side at both creation (POST) and update (PUT/PATCH). Client-supplied values for derived fields must be ignored. The canonical formulas are documented in `ValidationConstants.java`.
- **Validation engine**: When an experiment is finalized (status transition to OK or KO), `ValidationEngine.evaluate()` compares every MeasurementPair's cvPct and recoveryPct against the protocol's limits. Status is set programmatically — manual override requires electronic justification stored in `audit_log`.
- **DTO input validation**: Request DTOs use Jakarta Validation annotations (`@Positive`, `@NotNull`, `@NotBlank`) on all numeric fields with domain constraints. These annotations are the first line of defence before business logic runs.
- **Translatable error codes**: Every application exception must carry a constant error code string (e.g., `"ERR_CONCENTRATION_NOMINAL_ZERO"`). Controllers return this code in the error response body so the frontend can map it to a locale-specific message without parsing free-text.

## Critical Paths

- Documentation: `documentation/` (per-entity API contracts, ER diagrams, frontend wireframes)
- Database Migrations: `src/main/resources/db/migration/`
- Spring Configuration: `src/main/java/it/elismart_lims/config/` (GeminiConfig, SecurityConfig, HttpLoggingFilter)
- Security: `src/main/java/it/elismart_lims/security/` (JwtTokenProvider, JwtAuthFilter, UserDetailsServiceImpl)
- JPA Entities: `src/main/java/it/elismart_lims/model/`
- Repositories: `src/main/java/it/elismart_lims/repository/`
- Business Logic: `src/main/java/it/elismart_lims/service/`
- Validation Engine: `src/main/java/it/elismart_lims/service/validation/` (ValidationEngine, CurveFittingService, OutlierDetectionService)
- Curve Fitting: `src/main/java/it/elismart_lims/service/curve/` (FourPLFitter, FivePLFitter, LinearFitter, etc.)
- Import/Export: `src/main/java/it/elismart_lims/service/io/` (CsvImportService, PdfReportService, ExcelExportService)
- Audit: `src/main/java/it/elismart_lims/service/audit/` (AuditLogService)
- REST API Endpoints: `src/main/java/it/elismart_lims/controller/`
- DTOs: `src/main/java/it/elismart_lims/dto/`
- Mappers: `src/main/java/it/elismart_lims/mapper/`
- Exception Handling: `src/main/java/it/elismart_lims/exception/`
- Validation Constants: `src/main/java/it/elismart_lims/service/validation/ValidationConstants.java`
- Unit/Integration Tests: `src/test/java/` (JUnit 5 + Mockito)
- Frontend: `frontend/app.py` (entry point) + `frontend/pages/` (sub-pages)
- Frontend Config: `frontend/.streamlit/secrets.toml` (gitignored, contains `backend_url`)
- Frontend Dependencies: `frontend/requirements.txt`
- Assets: `assets/` (logo, images)

## Architecture Notes

### Domain Model

The system is a Laboratory Information Management System (LIMS) structured around these core concepts:

- **Protocol**: Defines the test methodology — curve type (`CurveType` enum), acceptable recovery/CV ranges, and required reagents via brake tables.
- **Experiment**: An actual instance of a protocol run on the lab bench, with metadata (date, operator, sample).
- **MeasurementPair**: A single replicate measurement with raw signals, mean, %CV, and %Recovery calculated server-side from raw values.
- **ReagentBatch**: Tracks reagent lot numbers for full traceability across experiments.
- **Sample**: Tracks sample identity (barcode, matrix type, patient/study ID, collection date, preparation method) linked to MeasurementPairs of type SAMPLE.
- **AuditLog**: Immutable change history for every field modification. Stores entity type, entity ID, field, old value, new value, changed by, timestamp, and reason.
- **User**: Authenticated user with role (ANALYST, REVIEWER, ADMIN). Linked to `createdBy`/`updatedBy` via Spring Security principal.

### CurveType Enum

`CurveType` (in `model/CurveType.java`) defines the calibration curve fitting model stored on each Protocol. Six values are supported:

| Enum constant             | Display name    | Description                                    |
|---------------------------|-----------------|------------------------------------------------|
| `FOUR_PARAMETER_LOGISTIC` | 4PL             | Symmetric sigmoid — ELISA standard             |
| `FIVE_PARAMETER_LOGISTIC` | 5PL             | Asymmetric sigmoid                             |
| `LOG_LOGISTIC_3P`         | 3PL             | 4PL with minimum fixed at zero                 |
| `LINEAR`                  | Linear          | Simple linear regression y = mx + q            |
| `SEMI_LOG_LINEAR`         | Semi-log Linear | Linear regression on log-transformed X axis    |
| `POINT_TO_POINT`          | Point-to-Point  | Non-parametric interpolation (not recommended) |

Each constant carries `displayName`, `description`, and `requiredParameters` fields. The CHECK constraint in `V4__add_curve_type_to_protocol.sql` mirrors this enum exactly. Always keep both in sync when adding new curve types.

### Validation Engine

`ValidationEngine` is the central component that enforces protocol acceptance criteria on experiment data. It is invoked automatically when an experiment's status transitions to **COMPLETED** (data submission). The engine then sets OK or KO programmatically — those statuses are never set directly by the operator.

**Workflow:**
1. `CurveFittingService` fits the calibration curve using CALIBRATION pairs and the protocol's `CurveType`. Fitted parameters are stored on the Experiment entity.
2. For each CONTROL and SAMPLE pair, the fitted curve interpolates the concentration from `signalMean`. `recoveryPct` is then computed as `(interpolatedConc / concentrationNominal) * 100`.
3. `OutlierDetectionService` applies the configured statistical test (Grubbs or %Difference threshold, per protocol setting) and flags outlier pairs with an audit trail entry.
4. `ValidationEngine.evaluate()` compares each non-outlier pair's `cvPct` and `recoveryPct` against protocol limits (`maxCvAllowed`, `maxErrorAllowed`). Individual pair results and overall experiment status are set programmatically.
5. Manual status override requires a reason string, which is persisted in `audit_log`.

**%CV formula:** `%CV = (SD / Mean) × 100` where `SD = |signal1 − signal2| / √2` for n=2 replicates. This is the ISO 5725 / CLSI EP15-A3 compliant definition. The formula is documented in `ValidationConstants.java`.

### Curve Fitting

Each `CurveType` has a corresponding fitter class implementing `CurveFitter` interface:
- `FourPLFitter`: fits `y = D + (A - D) / (1 + (x / C)^B)` using Levenberg-Marquardt optimization.
- `FivePLFitter`: adds asymmetry parameter E.
- `LinearFitter`, `SemiLogLinearFitter`, `PointToPointFitter`: simpler models.

Fitted parameters are stored as JSON in `Experiment.curveParameters` (column `curve_parameters`, type CLOB). Non-linear fitters (4PL, 5PL, 3PL) also store two diagnostic keys: `_convergence` (`1.0` = converged) and `_rms` (root-mean-square residual). The `CurveFittingService.interpolateConcentration(CurveType, double signal, CurveParameters)` method returns the back-calculated concentration.

### CSV Import

`CsvImportService` parses plate reader CSV output files and maps wells to MeasurementPairs. Supported formats:
- Generic (configurable column mapping)
- Tecan Magellan
- BioTek Gen5
- Molecular Devices SoftMax Pro

The import creates MeasurementPairs with raw signals populated from the file. All derived values (mean, %CV) are calculated server-side after import. The user maps the plate layout (which wells are calibrators, controls, samples) via a configuration step before import.

**Validation rule:** any row containing a **negative signal value** is rejected. The import collects all errors across all rows and reports them in a single `IllegalArgumentException` — never one-at-a-time. Signal value `0.0` is accepted.

### Export & Reporting

- `PdfReportService`: generates a PDF Certificate of Analysis containing experiment metadata, reagent lots, calibration curve plot, results table with color-coded QC status, and signature block.
- `ExcelExportService`: exports single or batch experiments to .xlsx with raw data, calculated metrics, and protocol limits on a summary sheet.

### Layer Structure

```
Controller (REST endpoints, request/response only)
  → Service (business logic, validation, orchestration)
    → Repository (Spring Data JPA interfaces)
      → Entity (JPA-annotated model classes)
```

The frontend (Streamlit) communicates exclusively via REST/JSON DTOs. No server-side rendering or templating on the backend.

### Request Flow

1. Streamlit sends HTTP request with JWT Bearer token → `JwtAuthFilter` validates token and sets `SecurityContextHolder`
2. Spring Boot Controller deserializes JSON into DTO
3. Controller delegates to Service — calls a service method that already returns a Response DTO
4. Service uses Mapper to convert DTO → Entity, applies business rules, persists via Repository
5. Service uses Mapper to convert Entity → DTO, returns to Controller
6. Controller wraps Response DTO in `ResponseEntity` — no mapping in controllers

### Authentication & Authorization

Spring Security with JWT stateless authentication:
- `POST /api/auth/login` — accepts username/password, returns JWT token
- `POST /api/auth/register` — admin-only, creates new user with role
- `JwtAuthFilter` — extracts and validates Bearer token on every request
- `SecurityConfig` — endpoint-level role restrictions (e.g., DELETE requires ADMIN, experiment approval requires REVIEWER)
- `AuditorAwareImpl` — reads authenticated username from `SecurityContextHolder` for `createdBy`/`updatedBy`

Three roles: `ANALYST` (create/edit experiments), `REVIEWER` (approve/reject experiments), `ADMIN` (user management, protocol CRUD, delete operations).

### Circular Dependency

`ExperimentService` → `ProtocolService` → `ExperimentService` is resolved by injecting `ExperimentService` into `ProtocolService` with `@Lazy`. This means Spring creates a proxy that is resolved on first use. Do not remove `@Lazy` from that constructor parameter.

### Database Schema Management

All DDL goes through named Flyway migrations (`V1__..., V2__..., ...`). JPA's `ddl-auto: validate` ensures entity mappings match the migrated schema. Never rely on Hibernate auto-ddl.

### N+1 Query Prevention

`ExperimentRepository` uses two `@EntityGraph` strategies:
- `findById`: fetches all three associations (protocol, usedReagentBatches, measurementPairs) in a single JOIN FETCH — safe for single-row lookups.
- `findAll(Specification, Pageable)`: fetches only the ManyToOne (`protocol`) via JOIN FETCH to avoid the Hibernate HHH90003004 cartesian-product warning; `@BatchSize(50)` on the OneToMany collections handles those in two follow-up batch queries.

`ProtocolReagentSpecRepository.findByProtocolId` uses `@EntityGraph(attributePaths = {"reagent"})` to prevent N+1 on lazy reagent loading.

Do not add a full `@EntityGraph` on paginated queries.

### Frontend-Backend Contract

The Streamlit app is a pure HTTP client. It never connects to the database directly.
Backend port: `8080`. Frontend reads `backend_url` from `secrets.toml` or `BACKEND_URL` env var, falling back to `http://localhost:8080`.

Frontend auth: every page calls `check_auth()` which verifies JWT token validity via session state. If no valid token, redirects to login page. Login page sends credentials to `/api/auth/login` and stores returned JWT. Sidebar contains a logout button that clears the token.

### AI Integration

`GeminiService` builds a three-section structured prompt (`[SYSTEM_CONTEXT]`, `[EXPERIMENT_DATA]`, `[USER_QUERY]`) and calls the LangChain4j `ChatLanguageModel` bean configured in `GeminiConfig`. The timeout is set to 120 seconds to accommodate long-running analysis. The application starts without a Gemini key (empty default) — the `/api/ai/analyze` endpoint will fail gracefully with a `GeminiServiceException` (502) if the key is missing or invalid. Gemini calls use retry with exponential backoff (3 attempts). AI analysis results are persisted in `AiInsight` entity linked to the experiment(s) analyzed, so they survive page refresh.

The AI prompt uses a generic "laboratory assay" context (not ELISA-specific) to support future assay types.

### HTTP Debug Logging

`HttpLoggingFilter` logs all HTTP requests and responses at DEBUG level. It is a no-op in non-debug environments (guarded by `log.isDebugEnabled()`). Body logging is capped at 10,000 bytes to avoid log flooding.

### API Documentation

- One Markdown file per entity under `documentation/` (e.g., `API-experiment.md`).
- `API.md` is an index linking to the per-entity docs.
- `database-er-diagram.md` contains the Mermaid ER diagram.
- `frontend-wireframes.md` documents the Streamlit page structure and navigation.
- `validation-formulas.md` documents every derived metric formula (%CV, %Recovery, curve fitting models) with references to ISO/CLSI standards. Includes sections on numerical safeguards and convergence metadata.
- `architecture-multi-assay.md` documents all ELISA-specific coupling points, the target `MeasurementStrategy` architecture for Phase 7, and the required DB migrations.
- Keep docs in sync when adding fields, endpoints, or enum values.

### Numerical Validation

Rules that apply to every arithmetic operation inside fitter classes (`FourPLFitter`, `FivePLFitter`, `LinearFitter`, etc.) and the validation engine:

- **NaN/Infinity guard**: every `Math.pow()`, division, or `Math.log()` call must be followed by a guard:
  ```java
  if (Double.isNaN(result) || Double.isInfinite(result))
      throw new IllegalArgumentException("<operation> produced NaN/Infinite: <context values>");
  ```
  The message must include the input values so the cause is diagnosable from logs alone.
- **Zero-concentration points**: calibration points with `concentrationNominal == 0` must be excluded from nonlinear fitting (4PL, 5PL, 3PL) before the optimisation loop. If such points exist, log a warning at `WARN` level and proceed with the remaining points. Do not silently include them — `Math.log(0)` is `-Infinity`.
- **%Recovery skip rule**: if `concentrationNominal <= 0` for a CONTROL or SAMPLE pair, skip the %Recovery calculation, set `recoveryPct = null`, and log a `WARN`-level message. Never divide by zero or produce `Infinity` in a persisted field.
- **Grubbs SD threshold**: the Grubbs outlier test uses `Math.abs(sd) < 1e-12` (not `sd == 0`) to detect degenerate distributions where all replicates are identical. When this threshold is met, skip the test and return no outliers.

### Experiment Status — State Machine

`ExperimentStatus` transitions are enforced by the service layer. Only the following transitions are valid:

| From               | To                                      | Trigger                          |
|--------------------|-----------------------------------------|----------------------------------|
| `PENDING`          | `COMPLETED`                             | Operator submits measurement data |
| `PENDING`          | `PENDING`                               | Draft edit (no-op transition)    |
| `COMPLETED`        | `OK`                                    | `ValidationEngine.evaluate()` passes |
| `COMPLETED`        | `KO`                                    | `ValidationEngine.evaluate()` fails |
| `COMPLETED`        | `VALIDATION_ERROR`                      | `ValidationEngine.evaluate()` throws |
| `OK`               | `PENDING`                               | Re-analysis requested            |
| `KO`               | `PENDING`                               | Re-analysis requested            |
| `VALIDATION_ERROR` | `PENDING`                               | Re-analysis requested            |

No other transition is permitted. Any attempt to move to an unlisted target state must throw an `IllegalStateException` with a descriptive message and error code.

**Frontend rule**: the experiment creation form (`add_experiment.py`) must never expose `OK`, `KO`, or `VALIDATION_ERROR` as selectable status options. Only `PENDING` is valid at creation time.

### First Startup and Authentication

`InitService` runs at application startup and seeds the database when the `app_user` table is empty:

- The admin username is `admin` with role `ADMIN`.
- The password is read from the `ADMIN_PASSWORD` environment variable.
- If `ADMIN_PASSWORD` is not set, a cryptographically random 16-character password is generated (`SecureRandom`).
- The generated password is printed to **stdout only**, surrounded by a visible ASCII box, so it is visible in the console at first boot.
- The password is **never** written to any log file (no `log.info()`, `log.warn()`, etc. on the password string). Use `System.out.println()` directly.

### Multi-assay Preparation (Future)

The current domain model is tightly coupled to the ELISA paradigm: `MeasurementPair` carries `signal1`/`signal2` (duplicate reads), `cvPct` (duplicate %CV), and `recoveryPct`. A future Phase 7 refactoring will introduce a `MeasurementStrategy` interface that decouples the measurement schema and metric formulas from the assay type. See `documentation/architecture-multi-assay.md` for the full coupling analysis, target design, and migration plan.

Until that refactoring is done, the following constraints apply to any code touching `MeasurementPair`, `ValidationEngine`, or `PairType`:

- **Do not increase ELISA coupling**: avoid adding new `if`/`switch` branches that check for ELISA-specific conditions by name or hardcoded constant. Prefer composable behaviour (strategy pattern, polymorphism) over conditional branching on type.
- **Prefer composition over inheritance**: new measurement behaviours should be added as injectable strategies, not as subclasses of `MeasurementPair` or `ValidationEngine`.
- **No hardcoded assay names**: strings like `"ELISA"` or `"PCR"` must never appear as literals in service or validation logic. Use enums or configuration.

## Guidelines & Constraints (What NOT to do)

- **No In-Memory DB**: Never use H2 in-memory mode. Data must persist in a local file (e.g., ./data/elismart_db).
- **No Logic in Controllers**: Business logic must reside in the Service layer.
- **No Hardcoded Secrets**: Never commit API keys, JWT secrets, or login credentials. Use env variables or gitignored secrets files.
- **No Manual SQL Outside Flyway**: Schema changes must go through numbered Flyway migrations. Use Spring Data JPA Query Methods for data access. Use @Query only for complex analytical queries.
- **No .streamlit/secrets.toml Commit**: The file is in `.gitignore`; defaults are communicated verbally or via secure channel.
- **Test Coverage**: Every new Service method must have a corresponding JUnit test case. Validation engine and curve fitting methods require parameterized tests with known reference datasets.
- **Controller security tests** (`@SpringBootTest` + `MockMvc`): Do NOT use `@WebMvcTest` with `SecurityMockMvcRequestPostProcessors.user()` for testing role-based access. With `SessionCreationPolicy.STATELESS`, the security context is stored via `RequestAttributeSecurityContextRepository`, so a mock user injected into the HTTP session is not picked up by `SecurityContextHolderFilter` — the request is evaluated as anonymous. Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with a mocked `JwtTokenProvider` that returns a fixed token, then pass that token as a `Bearer` header. This exercises `JwtAuthFilter` end-to-end and correctly enforces `@PreAuthorize` and URL-level rules. See `AuthControllerSecurityTest` and `ProtocolControllerSecurityTest` for the established pattern.
- **No Cross-Repository Injection**: Services must never inject a repository that belongs to a different domain. Call the owning service instead.
- **No Client-Supplied Derived Values**: signalMean, cvPct, and recoveryPct are always computed server-side. Ignore any client-supplied values for these fields at POST and PUT.
- **No Silent Overwrites**: Every PUT/PATCH that changes a persisted value must log the change via `AuditLogService.logChange()`. Direct repository `.save()` without audit logging is prohibited for update operations.
- **No Manual Status Override Without Reason**: Changing an experiment's status (especially from KO to OK) requires a `reason` string persisted in `audit_log`. The validation engine is the primary status setter.
- **No @Data on Entities**: Use @Getter @Setter on JPA entities. Implement equals/hashCode based on `id` field only.

## Project Status

Phases 1–5 complete. Development paused pending Phase 6 planning.

- **Phase 1 (Complete)**: Backend REST API with full CRUD for Protocol, ReagentCatalog, Experiment, and ProtocolReagentSpec. AI analysis via Google Gemini. Frontend 11-page Streamlit app with auth gate.
- **Phase 2 (Complete)**: Spring Security + JWT authentication, per-user audit trail (`createdBy`/`updatedBy` from SecurityContext), change history table (`audit_log`), CORS configuration, RBAC (Analyst/Reviewer/Admin), user management.
- **Phase 3 (Complete)**: Automated validation engine (OK/KO enforcement), 4PL/5PL/3PL/Linear/Semi-log/P2P curve fitting, %Recovery auto-calculation, outlier detection (Grubbs test), live %CV feedback during creation.
- **Phase 4 (Complete)**: CSV plate reader import (Tecan, BioTek, Molecular Devices), PDF Certificate of Analysis, Excel export (single + batch), Sample entity, reagent batch management, expiry alerts.
- **Phase 5 (Complete)**: QC color coding in results view, AI analysis shortcut from experiment detail, startup wrapper script, inline form validation, persistent error messages, edit/view mode distinction, UI tooltips and labels, post-save confirm flow, dashboard stats, sidebar user info.
- **Phase 6 (Planned)**: PostgreSQL migration, electronic signatures, multi-tenancy, compliance docs (21 CFR Part 11).
- **Phase 7 (Planned)**: PCR support, SaaS infrastructure, public API.

### Current Controllers
- Health (GET /api/health)
- Auth (POST /api/auth/login, POST /api/auth/register)
- User (GET /api/users, PUT /api/users/{id}/role, DELETE /api/users/{id})
- Protocol (GET all, GET /search paged, GET by ID, POST, PUT, DELETE)
- ReagentCatalog (GET all paged, GET by ID, POST, DELETE)
- ReagentBatch (GET by reagentId, GET /expiring, POST)
- ProtocolReagentSpec (GET by protocolId, POST)
- Experiment (GET by ID, POST, PUT, DELETE, POST /search, POST /{id}/validate, POST /{id}/import-csv)
- MeasurementPair (GET by experimentId, PUT /{id}, PATCH /{id}/outlier)
- Sample (GET by ID, POST)
- Export (GET /api/export/experiments/{id}/pdf, GET /api/export/experiments/{id}/xlsx, POST /api/export/experiments/xlsx)
- AI/Gemini (POST /api/ai/analyze, GET /api/ai/insights)

### Current Flyway Migrations
- V1: initial schema — `protocol`, `experiment`, `measurement_pair`, `reagent_catalog`, `protocol_reagent_spec`, `used_reagent_batch`
- V2: enum CHECK constraints on `curve_type`, `pair_type`, `status`
- V3: audit columns — `created_at`, `updated_at`, `created_by` on all tables
- V4: `curve_type` column on `protocol`
- V5: UNIQUE constraint on `reagent_catalog(name, manufacturer)`
- V6: NOT NULL constraints on `measurement_pair.signal_1` and `signal_2`
- V7: `app_user` table with `username`, `password`, `role`, `enabled`
- V8: `updated_by` column on all auditable tables
- V9: `audit_log` table
- V10: `curve_parameters` JSON column on `experiment`
- V11: `sample` table + `sample_id` FK on `measurement_pair`
- V12: `reagent_batch` table with `lot_number`, `expiry_date`, `supplier`, `notes`
- V13: migrate `used_reagent_batch` from inline lot columns to `reagent_batch_id` FK
- V14: `ai_insight` table + join table `ai_insight_experiments`

### Model Enums
- `ExperimentStatus` (5 values): PENDING, COMPLETED, OK, KO, VALIDATION_ERROR
- `PairType` (3 values): CALIBRATION, CONTROL, SAMPLE
- `CurveType` (6 values): FOUR_PARAMETER_LOGISTIC, FIVE_PARAMETER_LOGISTIC, LOG_LOGISTIC_3P, LINEAR, SEMI_LOG_LINEAR, POINT_TO_POINT
- `UserRole` (3 values): ANALYST, REVIEWER, ADMIN

### Frontend Pages
- `app.py`: Entry point with JWT-based login; navigation sections (ADD, SEARCH, DETAILS, AI, ADMIN)
- `pages/dashboard.py`: Health check, summary stats (protocol/experiment/OK/KO counts), expiry alerts, navigation
- `pages/add_protocol.py`: Protocol creation with curve type selector, inline reagent linking, curve description captions, post-save flow
- `pages/add_reagent.py`: Reagent catalog form with post-save flow
- `pages/add_experiment.py`: Experiment creation with reagent batches (mandatory badges), measurement pairs, live %CV, CSV import, post-save flow
- `pages/search_experiments.py`: Filtered search with pagination, multi-select counter, Details links, Compare/Export batch actions
- `pages/experiment_details.py`: Read/write experiment view with color-coded QC, edit/view distinction, status tooltip, protocol details expander, AI shortcut, PDF/Excel export
- `pages/compare_experiments.py`: Side-by-side comparison with lockable sections and Gemini AI analysis (persistent)
- `pages/search_protocols.py`: Protocol search with name filter and pagination
- `pages/protocol_details.py`: Read/write protocol view with reagent spec list, edit/delete mode
- `pages/search_reagents.py`: Reagent search with name/manufacturer filter and pagination
- `pages/user_management.py`: Admin-only user CRUD and role assignment

### Shared Utilities
- `frontend/utils.py` — global CSS palette, `resolve_backend_url()`, `check_auth()` (JWT validation), `render_logo()`, `render_sidebar()`, `color_code_qc()`, `show_persistent_error()`, `show_confirmation_dialog()`