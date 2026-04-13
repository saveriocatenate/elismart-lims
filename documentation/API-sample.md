# Sample API

Base path: `/api/samples`

Samples track the identity of biological specimens linked to `SAMPLE`-type Measurement Pairs,
enabling full chain-of-custody traceability.

## Endpoints

### GET /api/samples/{id}

Retrieve a sample by its ID.

**Path params**:
- `id` (Long) — the sample ID

**Response 200**:
```json
{
  "id": 1,
  "barcode": "SMP-2026-00123",
  "matrixType": "serum",
  "patientStudyId": "STUDY-A-001",
  "collectionDate": "2026-04-01",
  "preparationMethod": "centrifugation 2000×g 10 min"
}
```

**Response 404**: sample not found.

---

### POST /api/samples

Register a new sample.

**Request**:
```json
{
  "barcode": "SMP-2026-00124",
  "matrixType": "plasma",
  "patientStudyId": "STUDY-A-002",
  "collectionDate": "2026-04-02",
  "preparationMethod": null
}
```

**Fields**:
- `barcode` (String, required) — unique sample identifier; LIMS rejects duplicates
- `matrixType` (String, required) — biological matrix (e.g. `serum`, `plasma`, `urine`)
- `patientStudyId` (String, optional) — patient or study reference code
- `collectionDate` (LocalDate, optional) — ISO 8601 date
- `preparationMethod` (String, optional) — free-text description of sample preparation

**Response 201**: created sample with assigned `id`.

**Response 409**: barcode already registered.
