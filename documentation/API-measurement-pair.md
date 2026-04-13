# Measurement Pair API

Base path: `/api/measurement-pairs`

Measurement pairs are created implicitly when an Experiment is created (POST /api/experiments)
or via CSV import. These endpoints support read, update, and outlier flagging on individual pairs.

## Endpoints

### GET /api/measurement-pairs?experimentId={id}

Retrieve all measurement pairs for a given experiment.

**Query params**:
- `experimentId` (Long, required) — the parent experiment ID

**Response 200**:
```json
[
  {
    "id": 1,
    "experimentId": 10,
    "pairType": "CALIBRATION",
    "concentrationNominal": 200.0,
    "signal1": 0.45,
    "signal2": 0.47,
    "signalMean": 0.46,
    "cvPct": 3.04,
    "recoveryPct": 98.5,
    "pairStatus": "PASS",
    "isOutlier": false
  }
]
```

---

### PUT /api/measurement-pairs/{id}

Update the signal values and/or nominal concentration of a pair.
Server recalculates `signalMean` and `cvPct` from the new signals; `recoveryPct` is
not updated here (re-run validation to refresh it).

**Path params**:
- `id` (Long) — the measurement pair ID

**Request**:
```json
{
  "id": 1,
  "signal1": 0.50,
  "signal2": 0.52,
  "concentrationNominal": 200.0
}
```

**Response 200**: updated pair with recalculated `signalMean` and `cvPct`.

**Response 400**: pair does not belong to the specified experiment.

**Response 404**: pair not found.

---

### PATCH /api/measurement-pairs/{id}/outlier

Flag or unflag a measurement pair as an outlier. A reason is required when flagging.

**Path params**:
- `id` (Long) — the measurement pair ID

**Request**:
```json
{
  "isOutlier": true,
  "reason": "Pipetting error on S2 — signal 10× higher than expected"
}
```

**Response 200**: updated pair with `isOutlier` set and the action recorded in `audit_log`.

**Response 400**: `reason` is blank when `isOutlier` is `true`.

**Response 404**: pair not found.
