# Experiment API

Base path: `/api/experiments`

## Endpoints

### GET /api/experiments/{id}

Retrieve a single experiment by its ID.

**Path params**:
- `id` (Long) — the experiment ID

**Response 200**:
```json
{
  "id": 1,
  "name": "Run 2026-04-05",
  "date": "2026-04-05T10:00:00",
  "status": "OK",
  "protocolName": "IgG Test",
  "protocolCurveType": "FOUR_PARAMETER_LOGISTIC",
  "createdAt": "2026-04-05T10:00:00",
  "updatedAt": "2026-04-05T10:00:00",
  "createdBy": "system",
  "usedReagentBatches": [
    {
      "id": 1,
      "reagentName": "Anti-IgG Antibody",
      "lotNumber": "LOT-001",
      "expiryDate": "2027-01-01"
    }
  ],
  "measurementPairs": [
    {
      "id": 1,
      "pairType": "CALIBRATION",
      "concentrationNominal": 100.0,
      "signal1": 0.45,
      "signal2": 0.47,
      "signalMean": 0.46,
      "cvPct": 3.04,
      "recoveryPct": 98.5,
      "isOutlier": false
    }
  ]
}
```

**Response 404**: experiment not found

### POST /api/experiments

Create a new experiment with reagent batches and measurement pairs atomically.
Validates that the provided reagent batches cover all mandatory reagents defined by the protocol.

**Request**:
```json
{
  "name": "Run 2026-04-05",
  "date": "2026-04-05T10:00:00",
  "status": "OK",
  "protocolId": 1,
  "usedReagentBatches": [
    {
      "reagentId": 10,
      "lotNumber": "LOT-2025-001",
      "expiryDate": "2027-01-01"
    }
  ],
  "measurementPairs": [
    {
      "pairType": "CALIBRATION",
      "concentrationNominal": 100.0,
      "signal1": 0.45,
      "signal2": 0.47,
      "signalMean": 0.46,
      "cvPct": null,
      "recoveryPct": null,
      "isOutlier": false
    }
  ]
}
```

**Fields — `usedReagentBatches[]`**:
- `reagentId` (Long, required) — references a ReagentCatalog entry
- `lotNumber` (String, required) — the specific batch/lot identifier
- `expiryDate` (LocalDate, optional) — ISO 8601 date (`"2027-01-01"`)

**Fields — `measurementPairs[]`**:
- `pairType` (String, required) — one of `CALIBRATION`, `CONTROL`, `SAMPLE`
- `concentrationNominal` (Double, optional) — expected X-axis value
- `signal1`, `signal2` (Double) — raw OD readings
- `signalMean` (Double, optional) — `(signal1 + signal2) / 2`, can be computed client-side
- `cvPct`, `recoveryPct` (Double, optional)
- `isOutlier` (Boolean) — flag for manual or automatic outlier marking

**Status values**: `PENDING`, `COMPLETED`, `OK`, `KO`, `VALIDATION_ERROR`

**Response 201**: same shape as GET response

**Response 400**:
- Validation error (missing required fields)
- `ProtocolMismatchException`: `usedReagentBatches` does not cover all mandatory protocol reagents

**Response 404**: protocol or referenced reagent not found

### PUT /api/experiments/{id}

Update the mutable fields of an existing experiment.
The protocol and the set of linked reagents cannot be changed after creation.

**Path params**:
- `id` (Long) — the experiment ID

**Request**:
```json
{
  "name": "Updated Run Name",
  "date": "2026-04-07T00:00:00",
  "status": "OK",
  "reagentBatchUpdates": [
    {
      "id": 42,
      "lotNumber": "LOT-2026-NEW",
      "expiryDate": "2028-01-01"
    }
  ]
}
```

**Fields**:
- `name` (String, required) — new human-readable label
- `date` (LocalDateTime, required) — new experiment date/time
- `status` (String, required) — one of `PENDING`, `COMPLETED`, `OK`, `KO`, `VALIDATION_ERROR`
- `reagentBatchUpdates[]` (Array, required, may be empty):
  - `id` (Long, required) — ID of the `UsedReagentBatch` to update
  - `lotNumber` (String, required) — new lot number
  - `expiryDate` (LocalDate, optional) — new expiry date; `null` to clear

**Response 200**: same shape as GET response, reflecting updated values

**Response 400**: validation error or batch does not belong to this experiment

**Response 404**: experiment or batch not found

---

### DELETE /api/experiments/{id}

Delete an experiment by ID. Cascades to associated reagent batches and measurement pairs.

**Path params**:
- `id` (Long) — the experiment ID

**Response 204**: deleted successfully

**Response 404**: not found

### POST /api/experiments/search

Search experiments with optional filters and paginated results.

**Request**:
```json
{
  "name": "IgG",
  "date": null,
  "dateFrom": "2026-04-01T00:00:00",
  "dateTo": "2026-04-30T23:59:59",
  "status": "OK",
  "page": 0,
  "size": 20
}
```

**Fields**:
- `name` (String, optional) — partial match, case-insensitive
- `date` (LocalDateTime, optional) — exact date match; if provided, `dateFrom`/`dateTo` are ignored
- `dateFrom` (LocalDateTime, optional) — range start
- `dateTo` (LocalDateTime, optional) — range end
- `status` (String, optional) — exact match; one of `OK`, `KO`, `VALIDATION_ERROR`
- `page` (int) — zero-based page number
- `size` (int) — page size

Results are sorted by `date` descending.

**Response 200**:
```json
{
  "content": [
    { "id": 1, "name": "...", "date": "...", "status": "...", "protocolName": "...", "usedReagentBatches": [], "measurementPairs": [] }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3,
  "last": false
}
```

---

### POST /api/experiments/{id}/validate

Trigger the automated validation engine on an experiment. Fits the calibration curve,
back-interpolates concentrations for all non-outlier CONTROL and SAMPLE pairs, calculates
%Recovery, and sets the experiment status to `OK` or `KO`.

**Path params**:
- `id` (Long) — the experiment ID

**Response 200**: same shape as GET response, with updated `status`, `recoveryPct`, and `pairStatus` on each pair.

**Response 400**: no CALIBRATION pairs found; insufficient points for the curve type.

**Response 404**: experiment not found.

**Response 500**: curve fitting failed (e.g. optimizer did not converge).

---

### POST /api/experiments/{id}/import-csv

Import measurement pairs from a plate-reader CSV file into an existing experiment.
Supported formats: `GENERIC` (configurable column mapping), `TECAN`, `BIOTEK`, `SOFTMAX`.

**Path params**:
- `id` (Long) — the experiment ID (must already exist with 0 measurement pairs)

**Request**: `multipart/form-data` with two parts:
- `file` — the CSV file (`text/csv`)
- `config` — JSON configuration blob (`application/json`):

```json
{
  "format": "GENERIC",
  "wellColumn": "Well",
  "signal1Column": "Signal1",
  "signal2Column": "Signal2",
  "wellMapping": {
    "A1": { "pairType": "CALIBRATION", "concentrationNominal": 200.0 },
    "A2": { "pairType": "CALIBRATION", "concentrationNominal": 100.0 },
    "B1": { "pairType": "CONTROL",     "concentrationNominal": 50.0  },
    "C1": { "pairType": "SAMPLE",      "concentrationNominal": null  }
  }
}
```

**Response 200**: same shape as GET response, with `measurementPairs` populated from the import.

**Response 400**: missing required config fields; unknown well column; signal parsing error.

**Response 404**: experiment not found.

**Response 415**: unsupported file format.
