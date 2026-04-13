# Reagent Batch API

Base path: `/api/reagent-batches`

Reagent batches represent specific lot numbers of a catalog reagent, with expiry date and
supplier information. Each Experiment records which batches were used for full lot traceability.

## Endpoints

### GET /api/reagent-batches?reagentId={id}

List all registered batches for a given reagent catalog entry.

**Query params**:
- `reagentId` (Long, required) — the reagent catalog entry ID

**Response 200**:
```json
[
  {
    "id": 1,
    "reagentId": 10,
    "reagentName": "Anti-IgG Antibody",
    "lotNumber": "LOT-2026-001",
    "expiryDate": "2027-06-30",
    "supplier": "Sigma-Aldrich",
    "notes": "Aliquoted 2026-01-15"
  }
]
```

---

### GET /api/reagent-batches/expiring?daysAhead={n}

List all batches expiring within the next `daysAhead` days, sorted by expiry date ascending.
Used by the dashboard to render reagent expiry alerts.

**Query params**:
- `daysAhead` (int, required) — look-ahead window in days (e.g. `90`)

**Response 200**:
```json
[
  {
    "reagentName": "TMB Substrate",
    "manufacturer": "BioLegend",
    "lotNumber": "LOT-2025-010",
    "expiryDate": "2026-04-28",
    "daysUntilExpiry": 15
  }
]
```

---

### POST /api/reagent-batches

Register a new batch for a catalog reagent.

**Request**:
```json
{
  "reagentId": 10,
  "lotNumber": "LOT-2026-002",
  "expiryDate": "2027-12-31",
  "supplier": "Sigma-Aldrich",
  "notes": "Received 2026-04-01"
}
```

**Fields**:
- `reagentId` (Long, required) — references a `ReagentCatalog` entry
- `lotNumber` (String, required) — unique lot identifier for this reagent
- `expiryDate` (LocalDate, required) — ISO 8601 date (`"2027-12-31"`)
- `supplier` (String, optional)
- `notes` (String, optional)

**Response 201**: created batch with full details.

**Response 404**: reagent catalog entry not found.

**Response 409**: a batch with the same `lotNumber` already exists for this reagent.
