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
    "maxErrorAllowed": 10.0
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
  "maxErrorAllowed": 10.0
}
```

**Response 404**: protocol not found

### POST /api/protocols

Create a new protocol.

**Request**:
```json
{
  "name": "IgG Test",
  "numCalibrationPairs": 7,
  "numControlPairs": 3,
  "maxCvAllowed": 15.0,
  "maxErrorAllowed": 10.0
}
```

**Response 201**: same shape as GET response

**Response 400**: validation error (missing or invalid fields)

### DELETE /api/protocols/{id}

Delete a protocol by ID.

**Path params**:
- `id` (Long) — the protocol ID

**Response 204**: deleted successfully

**Response 404**: protocol not found
