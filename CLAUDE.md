# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

EliSmart database: a LIMS schema featuring hierarchical Protocols (defining curve-type, recovery/CV limits, and required reagents via brake tables) and Experiments. It tracks MeasurementPairs (replicates) with raw signals, mean, %CV, and %Recovery, linked to specific Reagent Batches for full lot-traceability.

## Tech Stack

- Backend: Java 21, Spring Boot 3.4.1 (Maven)
- Frontend: Python 3.14.3, Streamlit (multi-page app with session-based auth gate)
- Database: H2 Database (Embedded mode, file-based persistence)
- Migrations: Flyway (schema management via `src/main/resources/db/migration/`)
- AI: Google Gemini API tramite WebClient/Spring AI
- Tunnel/Deploy: Pinggy (for remote access)

## Environment Setup

**Backend** environment variables (set in shell or via `.env` file sourced before startup):
- `GEMINI_API_KEY` — Google Gemini API key (required for `/api/ai/analyze`)
- `GEMINI_BASE_URL` — override Gemini base URL (optional, defaults to `https://generativelanguage.googleapis.com`)
- `GEMINI_MODEL` — override Gemini model (optional, defaults to `gemini-flash-latest`)

See `.env.example` for the full list of variables and expected formats.

**Frontend** credentials live in `frontend/.streamlit/secrets.toml` (never committed). It contains `login_user`, `login_pass` (bcrypt hash), and `backend_url`.

## Development Conventions

- **Naming**: Java: PascalCase for classes, camelCase for variables and methods. Python: snake_case for scripts and functions. Database: snake_case for table and column naming (managed via Flyway migrations).
- **Architecture**: Standard Spring Boot Layered Architecture: Controller → Service → Repository → Entity.
- **Single Responsibility Principle**: Each service owns exactly one domain. If TrialService needs to read experiment data, it calls ExperimentService methods — it never injects ExperimentRepository directly. Only the owning service injects its repository.
- **DTO pattern**: Request DTOs are plain Java records (no Lombok). Response DTOs are Java records annotated with Lombok `@Builder` — no manual builder inner class. Call them as `ResponseDTO.builder().fieldName(val).build()` (no `withXxx` prefix).
- **DTO mapping via Mappers**: All DTO ↔ Entity construction and response mapping lives in dedicated Mapper classes under `mapper/`. Service orchestration methods must not contain builder() chains or `new` ResponseDTO constructor calls. Mappers use `ResponseDTO.builder().fieldName(val).build()`. Mapper classes must be static utility classes: all methods are `static`, the class is `final`, the private constructor throws `UnsupportedOperationException`. Mappers are never injected via `@Component` — call them as `MapperName.method()`.
- **Service mapping**: Every service public method returns DTOs (Response), never entities. Controllers call service methods and wrap results in `ResponseEntity`. All mapping (entity→DTO, DTO→entity) happens in the service layer via Mapper classes.
- **Audit trail**: All entities extend `Auditable` (`@MappedSuperclass`), which provides `createdAt`, `updatedAt`, and `createdBy` columns populated automatically by Spring Data JPA auditing. Never set these fields manually.
- **Data Transfer**: Strict use of DTOs for communication between Backend and Frontend.
- **Boilerplate**: Use Lombok (@Data, @Builder, @NoArgsConstructor, @AllArgsConstructor) to keep code clean.
- **Commit Messages**: Follow Conventional Commits (e.g., feat: implement excel parsing, fix: h2 connection string).
- **Language**: Code, Javadoc comments, and technical documentation must be in English. User-facing AI insights and logs may be in Italian if requested by the end-user.
- **Javadoc**: Every new or modified class, method, and field must include Javadoc. Update existing Javadoc when changing signatures or behavior. Public and protected members are mandatory; package-private and private are encouraged for non-trivial logic.
- **Pre-commit**: Before every commit, scan all untracked files against `.gitignore` for credentials, generated artifacts, IDE configs, and build output. Add missing patterns if any are found.

## Critical Paths

- Documentation: `documentation/` (per-entity API contracts, ER diagrams, frontend wireframes)
- Database Migrations: `src/main/resources/db/migration/`
- JPA Entities: `src/main/java/it/elismart_lims/model/`
- Repositories: `src/main/java/it/elismart_lims/repository/`
- Business Logic: `src/main/java/it/elismart_lims/service/`
- REST API Endpoints: `src/main/java/it/elismart_lims/controller/`
- DTOs: `src/main/java/it/elismart_lims/dto/`
- Mappers: `src/main/java/it/elismart_lims/mapper/`
- Exception Handling: `src/main/java/it/elismart_lims/exception/`
- Unit/Integration Tests: `src/test/java/` (JUnit 5 + Mockito, 99 tests)
- Frontend: `frontend/app.py` (dashboard) + `frontend/pages/` (sub-pages)
- Frontend Config: `frontend/.streamlit/secrets.toml` (gitignored, contains `login_user`, `login_pass`, `backend_url`)
- Frontend Dependencies: `frontend/requirements.txt`
- Assets: `assets/` (logo, images)

## Architecture Notes

### Domain Model

The system is a Laboratory Information Management System (LIMS) structured around these core concepts:

- **Protocol**: Defines the test methodology — curve type, acceptable recovery/CV ranges, and required reagents via brake tables.
- **Experiment**: An actual instance of a protocol run on the lab bench, with metadata (date, operator, sample).
- **MeasurementPair**: A single replicate measurement with raw signals, mean, %CV, and %Recovery calculated from the raw values.
- **ReagentBatch**: Tracks reagent lot numbers for full traceability across experiments.

### Layer Structure

```
Controller (REST endpoints, request/response only)
  → Service (business logic, validation, orchestration)
    → Repository (Spring Data JPA interfaces)
      → Entity (JPA-annotated model classes)
```

The frontend (Streamlit) communicates exclusively via REST/JSON DTOs. No server-side rendering or templating on the backend.

### Request Flow

1. Streamlit sends HTTP request → Spring Boot Controller deserializes JSON into DTO
2. Controller delegates to Service — calls a service method that already returns a Response DTO
3. Service uses Mapper to convert DTO → Entity, applies business rules, persists via Repository
4. Service uses Mapper to convert Entity → DTO, returns to Controller
5. Controller wraps Response DTO in `ResponseEntity` — no mapping in controllers

### Database Schema Management

All DDL goes through named Flyway migrations (`V1__..., V2__..., ...`). JPA's `ddl-auto: validate` ensures entity mappings match the migrated schema. Never rely on Hibernate auto-ddl.

### Frontend-Backend Contract

The Streamlit app is a pure HTTP client. It never connects to the database directly.
Backend port: `8080`. Frontend reads `backend_url` from `secrets.toml` or `BACKEND_URL` env var, falling back to `http://localhost:8080`.

Frontend auth: every page checks `st.session_state["authenticated"]` on load. If false, shows a login form that validates against credentials in `.streamlit/secrets.toml`. Sidebar contains a logout button. No registration — credentials are shared.

### API Documentation

- One Markdown file per entity under `documentation/` (e.g., `API-experiment.md`).
- `API.md` is an index linking to the per-entity docs.
- `database-er-diagram.md` contains the Mermaid ER diagram.
- `frontend-wireframes.md` documents the Streamlit page structure and navigation.

## Guidelines & Constraints (What NOT to do)

- **No In-Memory DB**: Never use H2 in-memory mode. Data must persist in a local file (e.g., ./data/elismart_db).
- **No Logic in Controllers**: Business logic must reside in the Service layer.
- **No Hardcoded Secrets**: Never commit API keys or login credentials. Use env variables or gitignored secrets files.
- **No Manual SQL Outside Flyway**: Schema changes must go through numbered Flyway migrations. Use Spring Data JPA Query Methods for data access. Use @Query only for complex analytical queries.
- **No .streamlit/secrets.toml Commit**: The file is in `.gitignore`; defaults are communicated verbally or via secure channel.
- **Test Coverage**: Every new Service method must have a corresponding JUnit test case.

## Project Status

Backend REST API implemented with full CRUD for Protocol, ReagentCatalog, Experiment, and ProtocolReagentSpec. AI analysis via Google Gemini. Frontend multi-page app with auth gate.

- **Controllers**: 
  - Health (GET /api/health)
  - Protocol (GET all, GET by ID, POST, DELETE)
  - ReagentCatalog (GET all paged, GET by ID, POST, DELETE)
  - ProtocolReagentSpec (GET by protocolId, POST)
  - Experiment (GET by ID, POST, PUT, DELETE, POST /search)
  - AI/Gemini (POST /api/ai/analyze)
- **Experiment POST**: validates mandatory reagent batches against protocol requirements
- **GlobalExceptionHandler**: maps ResourceNotFoundException, ProtocolMismatchException, GeminiServiceException, IllegalArgumentException, and validation errors to JSON
- **Flyway V1–V3**: full schema (6 tables) + enum constraints + audit columns
- **Frontend**: 8 Streamlit pages with shared header (logo), sidebar (logout), and login gate
  - `app.py`: Dashboard with health check + navigation buttons
  - `pages/add_protocol.py`: Protocol creation form with inline reagent linking
  - `pages/add_reagent.py`: Reagent catalog form
  - `pages/add_experiment.py`: Experiment creation form with reagent batches and measurement pairs
  - `pages/search_experiments.py`: Filtered search with pagination, Details links, and multi-select for comparison
  - `pages/experiment_details.py`: Read-only experiment view (metadata, pairs, batches)
  - `pages/compare_experiments.py`: Side-by-side comparison with lockable sections and Gemini AI analysis
  - `pages/search_protocols.py`: Placeholder (deferred)
  - `pages/search_reagents.py`: Placeholder (deferred)
- **Shared utilities**: `frontend/utils.py` — global CSS palette, `resolve_backend_url()`, `check_auth()`, `render_logo()`, `render_sidebar()`. All pages import from this module.
- **Tests**: 99 passing JUnit tests across controllers, services, mappers, utils, and exceptions
- **Remaining**: Protocol search UI, Reagent search UI, MeasurementPair CRUD endpoint, UsedReagentBatch CRUD endpoint
