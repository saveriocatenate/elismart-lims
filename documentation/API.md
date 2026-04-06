# API Documentation

REST API endpoints exposed by the EliSmart LIMS backend.

## Controllers

| Controller | File | Base Path |
|---|---|---|
| Health | [API-health.md](API-health.md) | `/api/health` |
| Protocol | [API-protocol.md](API-protocol.md) | `/api/protocols` |
| Reagent Catalog | [API-reagent-catalog.md](API-reagent-catalog.md) | `/api/reagent-catalogs` |
| Protocol Reagent Spec | [API-protocol-reagent-spec.md](API-protocol-reagent-spec.md) | `/api/protocol-reagent-specs` |
| Experiment | [API-experiment.md](API-experiment.md) | `/api/experiments` |
| AI Analysis (Gemini) | [API-ai.md](API-ai.md) | `/api/ai` |

## Database

- [ER Diagram](database-er-diagram.md) — Mermaid entity-relationship diagram

## Conventions

- All responses are JSON
- Errors are returned as `{"status", "error", "message", "timestamp"}`
- DELETE returns 204 No Content on success, 404 if the resource does not exist
