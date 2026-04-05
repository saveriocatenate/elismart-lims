# Reagent Catalog API

Base path: `/api/reagent-catalogs`

## Endpoints

### GET /api/reagent-catalogs

Retrieve all reagent catalogs.

**Request**: no body

**Response 200**:
```json
[
  {
    "id": 1,
    "name": "Anti-IgG Antibody",
    "manufacturer": "Sigma",
    "description": "Goat anti-human IgG"
  }
]
```

### GET /api/reagent-catalogs/{id}

Retrieve a single reagent catalog by ID.

**Path params**:
- `id` (Long) — the reagent catalog ID

**Response 200**:
```json
{
  "id": 1,
  "name": "Anti-IgG Antibody",
  "manufacturer": "Sigma",
  "description": "Goat anti-human IgG"
}
```

**Response 404**: reagent catalog not found

### POST /api/reagent-catalogs

Create a new reagent catalog.

**Request**:
```json
{
  "name": "Anti-IgG Antibody",
  "manufacturer": "Sigma",
  "description": "Goat anti-human IgG"
}
```

**Response 201**: same shape as GET response

**Response 400**: validation error (blank name or manufacturer)

### DELETE /api/reagent-catalogs/{id}

Delete a reagent catalog by ID.

**Path params**:
- `id` (Long) — the reagent catalog ID

**Response 204**: deleted successfully

**Response 404**: not found
