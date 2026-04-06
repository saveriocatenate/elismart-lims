# Protocol Reagent Spec API

Base path: `/api/protocol-reagent-specs`

Manages the binding between a Protocol and the ReagentCatalog entries it requires.
Each spec declares whether a reagent is mandatory (must be present when creating an experiment)
or optional.

## Endpoints

### GET /api/protocol-reagent-specs?protocolId={id}

Retrieve all reagent specifications for a given protocol.

**Query params**:
- `protocolId` (Long, required) — the protocol ID

**Response 200**:
```json
[
  {
    "id": 1,
    "protocolId": 1,
    "reagentId": 10,
    "reagentName": "Anti-IgG Antibody",
    "isMandatory": true
  },
  {
    "id": 2,
    "protocolId": 1,
    "reagentId": 20,
    "reagentName": "HRP Conjugate",
    "isMandatory": false
  }
]
```

Returns an empty list if the protocol has no reagent specs defined.

### POST /api/protocol-reagent-specs

Create a new protocol-reagent binding.

**Request**:
```json
{
  "protocolId": 1,
  "reagentId": 10,
  "isMandatory": true
}
```

**Fields**:
- `protocolId` (Long, required) — the protocol to bind
- `reagentId` (Long, required) — the reagent catalog entry to bind
- `isMandatory` (Boolean, required) — whether this reagent must appear in every experiment using this protocol

**Response 201**: same shape as a single element in the GET response

**Response 400**: validation error (null required field)

**Response 404**: protocol or reagent catalog not found
