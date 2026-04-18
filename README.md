# EliSmart 🧪🔬
### AI-Powered Biotech Dose-Response Analyzer

![EliSmart Logo](assets/EliSmartLogo.png)

**EliSmart** is a specialized Laboratory Information Management System (LIMS) designed to streamline the analysis of **ELISA dose-response assays**. Built for scientists and biotechnologists, it transforms raw laboratory data into validated, high-quality insights — from plate reader import to signed Certificate of Analysis — using a robust Java backend, an intuitive Streamlit frontend, and AI-powered analysis.

---

## 🌟 Key Features

* **Protocol-Based Management:** Define reusable assay templates with specific curve-fitting types (4PL, 5PL, 3PL, Linear, Semi-log, Point-to-Point), required reagents, and acceptance criteria (%CV and %Recovery limits).
* **Automated Validation Engine:** The system enforces protocol acceptance criteria automatically. Each measurement pair's %CV and %Recovery are compared against protocol limits — experiments are marked OK or KO programmatically. Manual overrides require a justification stored in the audit trail.
* **4PL Curve Fitting:** Built-in Levenberg-Marquardt optimization fits calibration curves to your standards, interpolates unknown sample concentrations, and calculates %Recovery automatically from raw signal data.
* **Plate Reader CSV Import:** Import raw signal data directly from Tecan, BioTek, Molecular Devices, or generic CSV formats. Map plate layout once, import in seconds — no manual transcription.
* **Replicate Analysis:** Handles Measurement Pairs (duplicates) with server-side calculation of Mean Signal, %CV (ISO 5725 compliant: SD/Mean × 100), and %Recovery.
* **Outlier Detection:** Configurable statistical tests (Grubbs, %Difference threshold) automatically flag outlier pairs. Manual override requires a reason stored in the audit trail.
* **Full Traceability:** Link every experiment to specific reagent Lot Numbers, expiration dates, and sample identities (barcode, matrix, study ID) for complete chain of custody.
* **Per-User Audit Trail:** Every record tracks who created it and who last modified it. Every field change is logged with old value, new value, user, timestamp, and reason. Built on Spring Security with JWT authentication.
* **Role-Based Access Control:** Three roles — Analyst (create/edit), Reviewer (approve/reject), Admin (user management, protocol CRUD) — with endpoint-level enforcement.
* **AI Insights:** Integrated with Google Gemini to provide qualitative analysis of curve anomalies, pipetting errors, lot-specific failures, and cross-experiment trends. Results are persistent and accessible from the experiment detail page.
* **Search & Compare:** Powerful filtering by date, name, or status, with the ability to compare up to four experiments side-by-side with color-coded QC thresholds.
* **Export & Reporting:** PDF Certificate of Analysis with calibration curve plot, color-coded results, and signature block. Excel export for downstream analysis in R, Python, or GraphPad.
* **Reagent Expiry Alerts:** Dashboard alerts for reagent lots expiring within 30, 60, or 90 days.

---

## 🏗️ Tech Stack

* **Backend:** Java 21 with Spring Boot 3.4, Spring Security + JWT
* **Frontend:** Python with Streamlit
* **Database:** H2 (Local/File-based for easy lab deployment)
* **Intelligence:** Google Gemini API via LangChain4j
* **Curve Fitting:** Levenberg-Marquardt (4PL, 5PL, 3PL, Linear, Semi-log, Point-to-Point)
* **Access:** Pinggy (Remote access tunneling)

---

## 📊 Data Model Logic

The system follows a strict hierarchical structure to ensure data integrity:

1. **Protocol:** The "Template" — defines curve type, acceptance criteria, and required reagents.
2. **Experiment:** The "Instance" — a single assay run linked to a protocol, with reagent batches and fitted curve parameters.
3. **MeasurementPair:** The core data unit — duplicate signals with server-calculated mean, %CV, %Recovery, and outlier status.
4. **Sample:** Tracks sample identity (barcode, matrix, patient/study ID) linked to sample-type measurement pairs.
5. **Reagent Catalog → Reagent Batch:** Master data and per-experiment lot traceability.
6. **Audit Log:** Immutable change history for every field modification across all entities.
7. **User:** Authenticated identity with role assignment (Analyst, Reviewer, Admin).

---

## 🚀 Getting Started

**Prerequisites:** Java 21+, Python 3.9+, Maven 3.8+

1. **Clone the repo:**
   ```bash
   git clone https://github.com/saveriocatenate/elismart-lims.git
   cd elismart-lims
   ```

2. **Set environment variables:** copy `.env.example` to `.env` and fill in the values you need.
   See the [Environment Variables](#environment-variables) section below for the full list.

3. **Build and run the backend:**
   ```bash
   source .env
   ./mvnw clean spring-boot:run
   ```

4. **Configure the frontend:** create `frontend/.streamlit/secrets.toml`:
   ```toml
   backend_url = "http://localhost:8080"
   ```

5. **Run the frontend:**
   ```bash
   cd frontend
   pip install -r requirements.txt
   streamlit run app.py
   ```

6. **Or use the startup script:**
   ```bash
   ./start.sh
   ```
   This launches both backend and frontend and waits for the backend health check before opening the UI.

7. **First login:** On first startup (empty database), an `admin` user with role `ADMIN` is created automatically.
   - If `ADMIN_PASSWORD` is set in the environment, that value is used as the password (BCrypt-hashed).
   - If `ADMIN_PASSWORD` is not set, a cryptographically random 16-character password is generated.
   - In both cases the password is printed **only to stdout** (never to log files) inside a visible ASCII box:
     ```
     ╔══════════════════════════════════════════════════════════════╗
     ║  PRIMO AVVIO — Utente admin creato                          ║
     ║  Username: admin                                            ║
     ║  Password: aK7x-mP2q-Rn4w-Ys8v                            ║
     ║  Salvala adesso, non verrà mostrata di nuovo.               ║
     ╚══════════════════════════════════════════════════════════════╝
     ```
   - On subsequent starts the seed step is skipped entirely (no reset, no log).

---

## Environment Variables

Copy `.env.example` to `.env` and source it before starting the backend (`source .env`).

### Backend (Spring Boot)

| Variable | Required | Default | Description |
|---|---|---|---|
| `JWT_SECRET` | **Yes** | — | HMAC-SHA256 signing key for JWT tokens. Must be at least 32 characters. Generate with `openssl rand -base64 32`. The application **refuses to start** if this is missing, blank, shorter than 32 chars, or a known placeholder. |
| `ADMIN_PASSWORD` | No | *(random)* | Password for the `admin` user created on first boot. If absent or blank, a cryptographically random 16-character password is generated and printed to **stdout only** (see [First Login](#getting-started) above). Ignored on subsequent starts. |
| `GEMINI_API_KEY` | No | *(empty)* | Google Gemini API key. Required for `POST /api/ai/analyze`. If absent, the endpoint returns HTTP 502; all other features work normally. |
| `GEMINI_MODEL` | No | `gemini-2.0-flash` | Overrides the Gemini model name. Only useful when testing with a specific model version. |
| `GEMINI_TIMEOUT_MS` | No | `120000` | Gemini HTTP read timeout in milliseconds (120 s). Increase for very large prompts or slow network links. |
| `JWT_EXPIRATION_MS` | No | `86400000` | JWT token lifetime in milliseconds. Default is 24 hours (86,400,000 ms). |
| `CORS_ALLOWED_ORIGIN` | No | `http://localhost:8501` | Origin allowed by the CORS policy. Must match the URL where the Streamlit frontend is served. **Required for any non-local deployment** (e.g., Pinggy tunnels, cloud hosting). |

### Frontend (Streamlit)

| Variable | Where to set | Description |
|---|---|---|
| `BACKEND_URL` | `frontend/.streamlit/secrets.toml` or env | Full URL of the Spring Boot backend. Default: `http://localhost:8080`. |

> **`secrets.toml` format:**
> ```toml
> backend_url = "http://localhost:8080"
> ```
> This file is gitignored and must never be committed.

---

## 👥 Multi-User Data Isolation

EliSmart LIMS supports multiple concurrent users with per-user data isolation in the
experiment search view.

**Default behaviour:** each user sees only their own experiments (those where `createdBy`
matches their username). The **Search Experiments** page opens with the "My experiments"
toggle enabled by default.

**Administrators:** the default is reversed — Admins see all experiments immediately
(toggle is off by default) to facilitate oversight and troubleshooting.

**Switching views:** any user can disable the "My experiments" toggle on the search page
to view all experiments they have permission to access (role-based rules still apply).

This behaviour is controlled server-side via the `mine` boolean field in `ExperimentSearchRequest`.
When `mine = true`, `ExperimentService` injects `createdBy = <authenticated username>` into
the JPA specification before querying.

---

## 📐 Concentration Units in Protocols

Each Protocol stores a `concentrationUnit` field that specifies the unit of measure for
nominal concentrations in that assay (e.g. `ng/mL`, `pg/mL`, `IU/L`, `nmol/L`).

The unit is set once at protocol creation and displayed throughout the application wherever
nominal or interpolated concentrations are shown. Pre-defined common units are available in
the protocol creation form; an "Other" option allows free-text entry for non-standard units.

The `concentrationUnit` field is informational — it is stored and displayed but does not
affect any arithmetic calculation in the validation engine or curve fitting.

---

## 📋 CSV Import Format

The CSV import (`POST /api/experiments/{id}/import-csv`) accepts files from Tecan Magellan,
BioTek Gen5, Molecular Devices SoftMax Pro, or any generic column-based format.

**Required columns** (column names are configurable in the import UI):

| Column | Description |
|---|---|
| Well identifier | Plate well address (e.g. `A1`, `B2`). |
| Signal 1 | First replicate absorbance reading. |
| Signal 2 | Second replicate absorbance reading. |

**Validation rules enforced at import time:**

- **Negative signal values are rejected.** Optical density readings must be ≥ 0.
  A negative value in any signal column causes the entire import to fail with an error
  message identifying the row(s) and exact value(s). All errors across all rows are
  reported in a single response — not one-at-a-time.
- Signal value `0.0` is accepted (blank well or background-subtracted reading).
- All three required columns must be present in the CSV header, otherwise the import is
  rejected before any row is processed.

---

## 🔄 Experiment Status Flow

Experiment statuses follow a strict state machine. The status can never jump arbitrarily
between states — only the transitions listed below are valid.

```
             ┌────────────────────────────────────────────┐
             │                                            │
             ▼                                            │ (re-analysis requested)
         PENDING  ──── (data entered) ────► COMPLETED ───┘
                                              │
                              ValidationEngine.evaluate()
                                    │        │        │
                                    ▼        ▼        ▼
                                   OK       KO   VALIDATION_ERROR
                                    │        │        │
                                    └────────┴────────┘
                                         (re-analysis)
                                              │
                                              ▼
                                           PENDING
```

| Status | Meaning |
|---|---|
| **PENDING** | Record created; measurement data not yet complete. Editable by Analysts. |
| **COMPLETED** | All signals entered. Triggers `ValidationEngine.evaluate()` automatically. |
| **OK** | All non-outlier CONTROL and SAMPLE pairs passed %CV and %Recovery criteria. |
| **KO** | At least one pair failed %CV or %Recovery. |
| **VALIDATION_ERROR** | Calculation could not complete (e.g. no calibration points, curve fit diverged). Check data and re-save. |

**Key rules:**
- The experiment creation form only exposes **PENDING** as the initial status.
  `OK`, `KO`, and `VALIDATION_ERROR` are set programmatically and are never selectable by the user.
- Moving an experiment back to `PENDING` (re-analysis) is allowed from `OK`, `KO`, or
  `VALIDATION_ERROR`.
- Manual status overrides (e.g. `KO` → `OK`) require a written justification stored in
  the audit log.

---

## 🔬 Typical Workflow

1. **Admin** creates a Protocol (e.g., "IL-6 ELISA Kit ABC") with 4PL curve type, %CV ≤ 10%, %Recovery 80–120%, and required reagents.
2. **Analyst** creates an Experiment from the protocol. Imports plate reader CSV or enters signals manually. System calculates mean, %CV, fits the curve, interpolates concentrations, and computes %Recovery.
3. **System** runs the Validation Engine: flags outliers, compares each pair against protocol limits, auto-sets status OK or KO.
4. **Analyst** reviews results with color-coded QC indicators. Asks Gemini AI for insights on anomalies.
5. **Reviewer** approves the experiment (status locked, electronic justification logged).
6. **Analyst** exports a PDF Certificate of Analysis for the batch record.

---

## 📁 Project Structure

```
elismart-lims/
├── src/main/java/it/elismart_lims/
│   ├── config/          # Spring config (Security, Gemini, CORS, Logging)
│   ├── security/        # JWT provider, auth filter, UserDetailsService
│   ├── controller/      # REST endpoints
│   ├── service/         # Business logic
│   │   ├── validation/  # ValidationEngine, constants, %CV/%Recovery formulas
│   │   ├── curve/       # CurveFitter interface + implementations (4PL, 5PL, etc.)
│   │   ├── io/          # CsvImportService, PdfReportService, ExcelExportService
│   │   └── audit/       # AuditLogService
│   ├── dto/             # Request/Response records
│   ├── mapper/          # DTO ↔ Entity mappers
│   ├── model/           # JPA entities
│   ├── repository/      # Spring Data JPA interfaces
│   └── exception/       # Custom exceptions + GlobalExceptionHandler
├── src/main/resources/
│   └── db/migration/    # Flyway SQL migrations (V1–V14)
├── src/test/java/       # JUnit 5 + Mockito tests
├── frontend/
│   ├── app.py           # Streamlit entry point
│   ├── pages/           # 12 Streamlit pages
│   ├── utils.py         # Shared utilities
│   └── requirements.txt
├── documentation/       # API contracts, ER diagram, wireframes, validation formulas
├── assets/              # Logo, images
├── start.sh             # One-command startup script
├── CLAUDE.md            # Claude Code development guide
└── README.md
```

---

## 🛡️ Security

- All REST endpoints are protected by JWT Bearer token authentication.
- Passwords are stored as bcrypt hashes.
- Role-based access control (ANALYST, REVIEWER, ADMIN) enforced at endpoint level.
- CORS configured for frontend origin.
- Gemini API key stored in environment variable, never committed.

---

## 📖 Documentation

- `documentation/API.md` — REST API index
- `documentation/API-*.md` — Per-entity endpoint contracts
- `documentation/database-er-diagram.md` — Mermaid ER diagram
- `documentation/frontend-wireframes.md` — Page structure and navigation
- `documentation/validation-formulas.md` — %CV, %Recovery, curve fitting formulas with ISO/CLSI references

---

## 🗺️ Roadmap

- [x] **Phase 1** — Core CRUD, Gemini AI integration, Streamlit frontend (11 pages)
- [x] **Phase 2** — Spring Security + JWT, audit trail, RBAC (Analyst/Reviewer/Admin), user management
- [x] **Phase 3** — Automated validation engine (OK/KO), 4PL/3PL/Linear/Semi-log curve fitting, outlier detection (Grubbs test), %Recovery auto-calculation
- [x] **Phase 4** — CSV plate reader import, PDF Certificate of Analysis, Excel export (single + batch), Sample entity, reagent batch management, expiry alerts
- [x] **Phase 5** — QC color coding, inline validation, persistent error messages, edit/view mode, UI tooltips, post-save flow, dashboard stats, sidebar user info
- [ ] **Phase 6** — PostgreSQL migration, electronic signatures, multi-tenancy, compliance docs (21 CFR Part 11)
- [ ] **Phase 7** — PCR support, SaaS infrastructure, public API

---

## 🛡️ License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

*Developed with ❤️ for the Biology & Biotech community.*
