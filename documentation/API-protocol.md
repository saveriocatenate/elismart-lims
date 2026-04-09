# Protocol API

Base path: `/api/protocols`

## Endpoints

### GET /api/protocols

Retrieve all protocols (unpaginated list for dropdowns and selection UIs).

**Response 200**:
```json
[
  {
    "id": 1,
    "name": "IgG Test",
    "numCalibrationPairs": 7,
    "numControlPairs": 3,
    "maxCvAllowed": 15.0,
    "maxErrorAllowed": 10.0,
    "curveType": "FOUR_PARAMETER_LOGISTIC"
  }
]
```

### GET /api/protocols/{id}

Retrieve a single protocol by its ID.

**Path params**:
- `id` (Long) — the protocol ID

**Response 200**:
```json
{
  "id": 1,
  "name": "IgG Test",
  "numCalibrationPairs": 7,
  "numControlPairs": 3,
  "maxCvAllowed": 15.0,
  "maxErrorAllowed": 10.0,
  "curveType": "FOUR_PARAMETER_LOGISTIC"
}
```

**Response 404**: protocol not found

### GET /api/protocols/search

Search protocols by partial name match (case-insensitive) with pagination.
Returns all protocols when `name` is absent or blank.

**Query params**:
- `name` (String, optional) — partial name filter, case-insensitive
- `page` (int, default 0) — zero-based page number
- `size` (int, default 20) — page size

**Response 200**:
```json
{
  "content": [
    {
      "id": 1,
      "name": "IgG Test",
      "numCalibrationPairs": 7,
      "numControlPairs": 3,
      "maxCvAllowed": 15.0,
      "maxErrorAllowed": 10.0,
      "curveType": "FOUR_PARAMETER_LOGISTIC"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

### POST /api/protocols

Create a new protocol.

**Request**:
```json
{
  "name": "IgG Test",
  "numCalibrationPairs": 7,
  "numControlPairs": 3,
  "maxCvAllowed": 15.0,
  "maxErrorAllowed": 10.0,
  "curveType": "FOUR_PARAMETER_LOGISTIC"
}
```

**Fields**:
- `name` (String, required) — unique protocol name
- `numCalibrationPairs` (Integer, required) — expected number of calibration replicate pairs per run
- `numControlPairs` (Integer, required) — expected number of control replicate pairs per run
- `maxCvAllowed` (Double, required) — maximum acceptable %CV between replicates (precision limit)
- `maxErrorAllowed` (Double, required) — maximum acceptable %Recovery error (accuracy limit)
- `curveType` (String, required) — calibration curve model; one of:
  - `FOUR_PARAMETER_LOGISTIC` — symmetric sigmoid (ELISA standard)
  - `FIVE_PARAMETER_LOGISTIC` — asymmetric sigmoid
  - `LOG_LOGISTIC_3P` — simplified 4PL with minimum fixed at zero
  - `LINEAR` — simple linear regression
  - `SEMI_LOG_LINEAR` — linear regression with log-transformed X-axis
  - `POINT_TO_POINT` — non-parametric interpolation (not recommended for high-precision analysis)

**Response 201**: same shape as GET response

**Response 400**: validation error (missing or invalid fields) or duplicate protocol

### PUT /api/protocols/{id}

Update an existing protocol. Blocked if any experiment is linked to this protocol —
remove all linked experiments first.

**Path params**:
- `id` (Long) — the protocol ID

**Request**: same shape as POST

**Response 200**: same shape as GET response, reflecting updated values

**Response 400**: validation error

**Response 404**: protocol not found

**Response 409**: experiments are linked to this protocol

### DELETE /api/protocols/{id}

Delete a protocol by ID. Blocked if any experiment is linked to this protocol.

**Path params**:
- `id` (Long) — the protocol ID

**Response 204**: deleted successfully

**Response 404**: protocol not found

**Response 409**: experiments are linked to this protocol
