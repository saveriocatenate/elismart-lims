# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

EliSmart database: a LIMS schema featuring hierarchical Protocols (defining curve-type, recovery/CV limits, and required reagents via bridge tables) and Experiments. It tracks MeasurementPairs (replicates) with raw signals, mean, %CV, and %Recovery, linked to specific Reagent Batches for full lot-traceability.
## Tech Stack

- Backend: Java 21, Spring Boot 3.4.1 (Maven)
- Frontend: Python 3.14.3, Streamlit
- Database: H2 Database (Embedded mode, file-based persistence)
- Migrations: Flyway (schema management via `src/main/resources/db/migration/`)
- AI: Google Gemini API tramite WebClient/Spring AI
- Tunnel/Deploy: Pinggy (for remote access)

## Environment Setup

Configuration is stored in `openrouter.env`:
- `ANTHROPIC_BASE_URL` — points to `https://openrouter.ai/api`
- `ANTHROPIC_API_KEY` — OpenRouter API key
- `ANTHROPIC_MODEL` — model identifier

Source `openrouter.env` before running the application to load environment variables.

## Development Conventions
- **Naming**: - Java: PascalCase for classes (ExperimentService), camelCase for variables and methods. Python: snake_case for scripts and functions. Database: snake_case for table and column naming (managed via Flyway migrations).
- **Architecture**: Standard Spring Boot Layered Architecture: Controller → Service → Repository → Entity.
- **Single Responsibility Principle**: Each service owns exactly one domain. If TrialService needs to read experiment data, it calls ExperimentService methods — it never injects ExperimentRepository directly. Only the owning service injects its repository.
- **DTO mapping via Mappers**: All DTO ↔ Entity construction and response mapping lives in dedicated Mapper classes under `mapper/`. Service orchestration methods must not contain builder() chains. Create a new Mapper whenever a DTO conversion is needed. Mapper classes must be static utility classes: all methods are `static`, the class is `final`, the private constructor throws `UnsupportedOperationException`. Mappers are never injected via `@Component` or `@RequiredArgsConstructor` — call them as `MapperName.method()`.
- **No nested classes/records**: Never use nested classes or records. Extract every class and record to its own file. DTO sub-records (e.g., `ExperimentResponse.VariableSummary`) become standalone files (e.g., `ExperimentVariableSummary.java`). Exception classes live in `exception/model/`, **never** nested inside Service classes.
- **Data Transfer**: Strict use of DTOs for communication between Backend and Frontend.
- **Boilerplate**: Use Lombok (@Data, @Builder, @NoArgsConstructor, @AllArgsConstructor) to keep code clean.
- **Commit Messages**: Follow Conventional Commits (e.g., feat: implement excel parsing, fix: h2 connection string).
- **Language**: Code, Javadoc comments, and technical documentation must be in English. User-facing AI insights and logs may be in Italian if requested by the end-user.
- **Javadoc**: Every new or modified class, method, and field must include Javadoc. Update existing Javadoc when changing signatures or behavior. Public and protected members are mandatory; package-private and private are encouraged for non-trivial logic.
- **Pre-commit**: Before every commit, scan all untracked files against `.gitignore` for credentials, generated artifacts, IDE configs, and build output. Add missing patterns if any are found.

## Critical Paths
- Documentation: `documentation/` (per-entity API contracts, ER diagrams)
- Database Migrations: `src/main/resources/db/migration/`
- JPA Entities: `src/main/java/it/elismart_lims/model/`
- Business Logic: `src/main/java/it/elismart_lims/service/`
- REST API Endpoints: `src/main/java/it/elismart_lims/controller/`
- DTOs: `src/main/java/it/elismart_lims/dto/`
- Unit/Integration Tests: `src/test/java/` (JUnit 5 + Mockito)

## Architecture Notes

### Domain Model

The system is a Laboratory Information Management System (LIMS) structured around these core concepts:
- **Protocol**: Defines the test methodology — curve type, acceptable recovery/CV ranges, and required reagents via bridge tables.
- **Experiment**: An actual instance of a protocol run on the lab bench, with metadata (date, operator, sample).
- **MeasurementPair**: A single replicate measurement with raw signals, mean, %CV, and %Recovery calculated from the raw values.
- **ReagentBatch**: Tracks reagent lot numbers for full traceability across experiments.
- **Trial**: Groups measurements within an experiment (e.g., calibration curve points, QC samples).

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
2. Controller delegates to Service (no logic in controllers)
3. Service uses Mapper to convert DTO → Entity, applies business rules, persists via Repository
4. Service uses Mapper to convert Entity → DTO, returns to Controller
5. Controller serializes DTO to JSON response

### Database Schema Management

All DDL goes through named Flyway migrations (`V1__..., V2__..., ...`). JPA's `ddl-auto: validate` ensures entity mappings match the migrated schema. Never rely on Hibernate auto-ddl.

### Frontend-Backend Contract

The Streamlit app is a pure HTTP client. It never connects to the database directly.
Backend port: `8080`. Frontend reads `backend_url` from `secrets.toml` or defaults to `http://localhost:8080`.

### API Documentation

- One Markdown file per entity under `documentation/` (e.g., `API-experiment.md`).
- `API.md` is an index linking to the per-entity docs.
- `database-er-diagram.md` contains the Mermaid ER diagram.

## Guidelines & Constraints (What NOT to do)
- No In-Memory DB: Never use H2 in-memory mode. Data must persist in a local file (e.g., ./data/elismart_db).
- No Logic in Controllers: Business logic must reside in the Service layer.
- No Hardcoded Secrets: Never commit API keys. Use environment variables.
- No Manual SQL Outside Flyway: Schema changes must go through numbered Flyway migrations. Use Spring Data JPA Query Methods for data access. Use @Query only for complex analytical queries.
- Test Coverage: Every new Service method must have a corresponding JUnit test case.
- No Commit configuration: Never commit openrouter.env and settings.json file

## Project Status

Skeleton (empty shell) created: Spring Boot entry point, health endpoint, Flyway baseline, Streamlit welcome page. No domain logic implemented yet.
