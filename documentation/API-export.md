# Export API

Base path: `/api/export`

Generates downloadable reports for experiments. All endpoints stream binary content directly.

## Endpoints

### GET /api/export/experiments/{id}/pdf

Generate a PDF Certificate of Analysis (CoA) for a single experiment.

**Path params**:
- `id` (Long) — the experiment ID

**Response 200**:
- `Content-Type: application/pdf`
- `Content-Disposition: attachment; filename="CoA_experiment_{id}.pdf"`
- Body: PDF binary

The CoA includes:
- Experiment metadata (name, date, protocol, operator)
- Reagent lot numbers and expiry dates
- Calibration curve plot (Plotly-rendered)
- Results table with color-coded QC status (PASS/FAIL per pair)
- Electronic signature block

**Response 404**: experiment not found.

**Response 500**: PDF generation failed.

---

### GET /api/export/experiments/{id}/xlsx

Export a single experiment to Excel (.xlsx).

**Path params**:
- `id` (Long) — the experiment ID

**Response 200**:
- `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition: attachment; filename="experiment_{id}.xlsx"`
- Body: XLSX binary

The workbook contains:
- **Summary** sheet: metadata, protocol limits, overall status
- **Calibration** sheet: calibration pair signals and curve parameters
- **Results** sheet: all pairs with %CV, %Recovery, and QC color coding

**Response 404**: experiment not found.

---

### POST /api/export/experiments/xlsx

Batch export multiple experiments to a single Excel workbook.

**Request**:
```json
[1, 3, 7, 12]
```

A JSON array of experiment IDs (1–50 experiments per request).

**Response 200**:
- `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition: attachment; filename="experiments_batch.xlsx"`
- Body: XLSX binary with one sheet per experiment plus an index sheet.

**Response 400**: empty ID list or more than 50 experiments requested.

**Response 404**: one or more experiment IDs not found.
