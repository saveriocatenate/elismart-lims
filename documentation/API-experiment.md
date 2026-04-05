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

Create a new experiment with associated reagent batches and measurement pairs.
Validates that the provided reagent batches cover all mandatory reagents defined by the protocol.

**Request**:
```json
{
  "name": "Run 2026-04-05",
  "date": "2026-04-05T10:00:00",
  "status": "OK",
  "protocolId": 1,
  "usedReagentBatchIds": [1],
  "measurementPairs": [
    {
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

**Response 201**: same shape as GET response

**Response 400**:
- Validation error (missing required fields)
- `ProtocolMismatchException`: used reagent batches don't cover all mandatory protocol reagents

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
- `date` (LocalDateTime, optional) — exact date match
- `dateFrom` (LocalDateTime, optional) — range start (ignored if `date` is provided)
- `dateTo` (LocalDateTime, optional) — range end (ignored if `date` is provided)
- `status` (String, optional) — exact match on status
- `page` (int) — zero-based page number, defaults to `0` when omitted from record
- `size` (int) — page size

Results are sorted by `date` descending. If `date` is specified, `dateFrom`/`dateTo` are ignored.

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
