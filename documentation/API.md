# API Documentation

REST API endpoints exposed by the EliSmart LIMS backend.

## Controllers

| Controller | File | Base Path |
|---|---|---|
| Health | [API-health.md](API-health.md) | `/api/health` |
| Auth | [API-auth.md](API-auth.md) | `/api/auth` |
| User | [API-user.md](API-user.md) | `/api/users` |
| Protocol | [API-protocol.md](API-protocol.md) | `/api/protocols` |
| Reagent Catalog | [API-reagent-catalog.md](API-reagent-catalog.md) | `/api/reagent-catalogs` |
| Reagent Batch | [API-reagent-batch.md](API-reagent-batch.md) | `/api/reagent-batches` |
| Protocol Reagent Spec | [API-protocol-reagent-spec.md](API-protocol-reagent-spec.md) | `/api/protocol-reagent-specs` |
| Experiment | [API-experiment.md](API-experiment.md) | `/api/experiments` |
| Measurement Pair | [API-measurement-pair.md](API-measurement-pair.md) | `/api/measurement-pairs` |
| Sample | [API-sample.md](API-sample.md) | `/api/samples` |
| Export | [API-export.md](API-export.md) | `/api/export` |
| AI Analysis (Gemini) | [API-ai.md](API-ai.md) | `/api/ai` |

## Database

- [ER Diagram](database-er-diagram.md) — Mermaid entity-relationship diagram (V1–V14 migrations)

## User Documentation

- [User Guide](user-guide.md) — End-user guide for scientists: workflow, experiment creation, validation, export, AI analysis

## Architecture & Planning

- [Multi-Assay Architecture](architecture-multi-assay.md) — Phase 7 coupling analysis: ELISA-specific assumptions, target `MeasurementStrategy` design, DB migration plan, and impact estimate

## Conventions

- All responses are JSON
- Errors are returned as `{"status", "error", "message", "timestamp"}`
- DELETE returns 204 No Content on success, 404 if the resource does not exist
- All endpoints require a `Authorization: Bearer <jwt>` header except `POST /api/auth/login`
- Role requirements: ANALYST (read + create + edit), REVIEWER (approve), ADMIN (user management + DELETE)
