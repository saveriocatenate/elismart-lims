# Multi-Assay Architecture — Coupling Analysis & Target Design

> **Status:** Planning document for the future multi-assay refactor (PCR support, multi-assay SaaS).
> No code refactoring is described here — this document records the *intent*
> and the *cost* so that future development can proceed with full context.

---

## 1. Current ELISA Coupling Points

Every entry below is a point where the code hardcodes the assumption that
a measurement consists of **exactly two duplicate readings** (`signal1`, `signal2`)
and that precision is expressed as **%CV computed via the ISO 5725 n=2 formula**
(`SD = |s1 − s2| / √2`).

Difficulty legend: **E** = easy (decouple without touching DB) |
**M** = medium (logic change only, no schema change) |
**H** = hard (requires DB migration + API change + frontend change).

### 1.1 Data Model & Entity Layer

| File | Line | Assumption | Difficulty |
|------|------|-----------|-----------|
| `model/MeasurementPair.java` | 43–44 | Fields `signal1`, `signal2` — exactly 2 signals per row | **H** |
| `model/MeasurementPair.java` | 51–52 | `signalMean` — arithmetic mean of exactly 2 values | **H** |
| `model/MeasurementPair.java` | 54–56 | `cvPct` — %CV from n=2 ISO 5725 formula | **H** |
| `model/MeasurementPair.java` | 58–60 | `recoveryPct` — interpolated concentration / nominal | **M** |

### 1.2 Validation Constants & Formula Layer

| File | Line | Assumption | Difficulty |
|------|------|-----------|-----------|
| `service/validation/ValidationConstants.java` | 16 | `SQRT_2` — denominator for n=2 SD formula | **H** |
| `service/validation/ValidationConstants.java` | 31–33 | `calculateSignalMean(signal1, signal2)` — 2-arg signature | **H** |
| `service/validation/ValidationConstants.java` | 54–61 | `calculateCvPercent(signal1, signal2)` — 2-arg, divides by √2 | **H** |

### 1.3 DTO Layer

| File | Line | Assumption | Difficulty |
|------|------|-----------|-----------|
| `dto/MeasurementPairRequest.java` | 18–19 | `@NotNull Double signal1, signal2` — 2-field request schema | **H** |
| `dto/MeasurementPairUpdateRequest.java` | 16, 19 | Same — update schema locked to 2 signals | **H** |
| `dto/MeasurementPairResponse.java` | 17–18 | `Double signal1, signal2` in response | **H** |
| `dto/CsvImportConfig.java` | 29–30 | `signal1Column`, `signal2Column` — 2-column CSV mapping | **H** |

### 1.4 Mapper Layer

| File | Line | Assumption | Difficulty |
|------|------|-----------|-----------|
| `mapper/MeasurementPairMapper.java` | 43–51 | Calls `calculateSignalMean(s1, s2)` and `calculateCvPercent(s1, s2)` | **M** |

### 1.5 Service Layer

| File | Line | Assumption | Difficulty |
|------|------|-----------|-----------|
| `service/MeasurementPairService.java` | 68–71 | Audit field names hardcoded as `"signal1"`, `"signal2"` | **M** |
| `service/MeasurementPairService.java` | 77–80 | Recalculates mean/cv using 2-signal formulas | **M** |
| `service/io/CsvImportService.java` | 81–82, 114–115, 141–142 | Expects exactly `signal1Column` and `signal2Column` in CSV | **H** |

### 1.6 Validation Engine

| File | Line | Assumption | Difficulty |
|------|------|-----------|-----------|
| `service/validation/ValidationEngine.java` | 129 | Uses `pair.getSignalMean()` for back-interpolation | **M** |
| `service/validation/ValidationEngine.java` | 147–148 | Recovery formula: `(interpolated / nominal) × 100` — single scalar | **M** |
| `service/validation/OutlierDetectionService.java` | 23, 28 | Comment: Grubbs not valid for n=2; CV threshold is the primary test | **M** |
| `service/validation/OutlierDetectionService.java` | 91–95 | Flags pairs where `cvPct > maxCvAllowed` — assumes cvPct always present | **M** |

### 1.7 Export Services

| File | Line | Assumption | Difficulty |
|------|------|-----------|-----------|
| `service/io/PdfReportService.java` | 362–363 | PDF table has 3 hardcoded signal columns (signal1, signal2, mean) | **M** |
| `service/io/ExcelExportService.java` | 222–224, 274–276 | Excel rows use hardcoded column positions for signal1/signal2/mean | **M** |

### 1.8 Frontend (Python / Streamlit)

| File | Line | Assumption | Difficulty |
|------|------|-----------|-----------|
| `frontend/pages/add_experiment.py` | 136–140 | Live `_compute_cv(s1, s2)` uses `abs(s1-s2)/sqrt(2)/mean * 100` | **H** |
| `frontend/pages/add_experiment.py` | 452 | Constructs pair payload with `"signal1": s1, "signal2": s2` | **H** |
| `frontend/pages/add_experiment.py` | 536–541 | CSV import UI has "Colonna Segnale 1" / "Colonna Segnale 2" | **H** |
| `frontend/pages/add_experiment.py` | 693–694 | CSV config body: `"signal1Column"`, `"signal2Column"` | **H** |
| `frontend/pages/experiment_details.py` | 371–372 | Reads `cvPct`, `recoveryPct` from API response | **M** |
| `frontend/pages/experiment_details.py` | 389–391 | HTML table: 3 signal columns hardcoded | **M** |
| `frontend/pages/experiment_details.py` | 546, 554, 592–593 | Edit mode: separate inputs for `signal1`/`signal2` | **H** |
| `frontend/pages/compare_experiments.py` | 338–346 | Displays Seg.1, Seg.2, Media, %CV, %Rec. as fixed columns | **M** |
| `frontend/pages/compare_experiments.py` | 364–365, 394–396 | Extracts and plots `signal1`, `signal2`, `signalMean`, `cvPct` by name | **M** |

---

## 2. Target Architecture

The goal of the future multi-assay refactor is to introduce a `MeasurementStrategy` abstraction that
decouples the measurement schema (how many signals, which formula) from the
rest of the system. The invariant that must be preserved is: everything
*outside* the measurement layer (curve fitting, validation rules, audit trail,
export) only sees computed scalars (`signalMean`, `cvPct`, `recoveryPct`) —
never the raw signal array.

### 2.1 Conceptual Class Diagram (ASCII)

```
                        ┌─────────────────────────────────────────────────────────┐
                        │              MeasurementStrategy  (interface)            │
                        │                                                           │
                        │  + replicateCount() : int                                │
                        │  + computeMean(signals: List<Double>) : double           │
                        │  + computeCv(signals: List<Double>) : double             │
                        │  + metricsLabel() : String   // e.g. "%CV", "Efficiency" │
                        │  + signalColumnLabels() : List<String>                   │
                        └───────────────────────────────┬─────────────────────────┘
                                                        │  implements
                          ┌─────────────────────────────┴──────────────────────────────────┐
                          │                                                                  │
              ┌───────────▼──────────────┐                             ┌────────────────────▼──────┐
              │  ElisaDuplicateStrategy  │                             │  PcrEfficiencyStrategy     │
              │  (n=2, ISO 5725 formula) │                             │  (n≥2, E=10^(-1/slope)−1)  │
              │                          │                             │                            │
              │  replicateCount() = 2    │                             │  replicateCount() = 3..6   │
              │  computeCv = |s1-s2|/√2  │                             │  computeCv = std(signals)  │
              └──────────────────────────┘                             └────────────────────────────┘

                        ┌─────────────────────────────────────────────────────────┐
                        │              MeasurementPair  (entity, unchanged)        │
                        │                                                           │
                        │  + signal1, signal2          (ELISA columns, kept)       │
                        │  + signals: JSONB  (NEW — nullable; stores n>2 signals)  │
                        │  + signalMean                (computed scalar)            │
                        │  + cvPct                     (computed scalar)            │
                        │  + recoveryPct               (computed scalar)            │
                        └─────────────────────────────────────────────────────────┘

                        ┌─────────────────────────────────────────────────────────┐
                        │              ValidationEngine  (service, unchanged API)  │
                        │                                                           │
                        │  evaluate(experiment, protocol)                          │
                        │    → reads only: signalMean, cvPct, recoveryPct         │
                        │    → no reference to signal1/signal2                     │
                        │                                                           │
                        │  The strategy is resolved by:                            │
                        │    Protocol.assayType → AssayTypeRegistry.strategy()    │
                        └─────────────────────────────────────────────────────────┘
```

### 2.2 Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Keep `signal1`/`signal2` columns in DB for backward compat | Avoids a lossy migration for all existing ELISA data |
| Add nullable `signals` JSONB column for n>2 | Forward compat without breaking existing rows |
| Strategy resolved at Protocol level, not MeasurementPair | A protocol defines one assay type; all its pairs share the same measurement model |
| `ValidationEngine` reads only computed scalars | Engine has zero assay-type coupling today; preserve that invariant |
| `CurveFittingService` is already assay-agnostic | It takes `(concentration, signal)` pairs — no change needed |

### 2.3 `AssayType` Enum (proposed)

```java
public enum AssayType {
    ELISA_DUPLICATE("ELISA Duplicate", 2, ElisaDuplicateStrategy.class),
    PCR_STANDARD    ("PCR Standard Curve", 3, PcrEfficiencyStrategy.class);
    // ...
}
```

This enum replaces any implicit assumption of n=2 throughout the codebase.
Adding PCR support becomes: implement a new `MeasurementStrategy`, add an enum
constant, write a Flyway migration adding the column, and register it — the
rest of the stack is untouched.

---

## 3. Required DB Migrations (Conceptual)

| Migration | Description | Reversible? |
|-----------|-------------|-------------|
| V15: add `assay_type` column on `protocol` | `VARCHAR(50) NOT NULL DEFAULT 'ELISA_DUPLICATE'` | Yes |
| V16: add `signals` column on `measurement_pair` | `JSONB NULL` — stores signal array for n>2 assays | Yes |
| V17: backfill `signals` from `signal1/signal2` | `signals = '[signal1, signal2]'` for all existing rows | Yes (drop column) |
| V18: (optional) drop `signal1`/`signal2` | Only once all clients use `signals` array | **No — data loss** |

> **Note on H2 compatibility:** H2 does not support JSONB natively. For
> the local H2 deployment, the column should be declared as `JSON` or
> `CLOB` with application-level serialization. For PostgreSQL (future
> production), `JSONB` with GIN indexing is the target. The Flyway
> migration script may require a profile-specific variant
> (`V16__add_signals_h2.sql` vs `V16__add_signals_postgres.sql`) when
> implementation begins.

> **Recommendation:** Never run V18. Retain `signal1`/`signal2` as
> denormalized columns synchronized at write time from `signals[0]` and
> `signals[1]`. This preserves backward compatibility with existing
> export templates and audit log field names without relying on
> database-specific materialized view features.

---

## 4. Impact Estimate

| Scope | Files affected | Effort estimate |
|-------|----------------|-----------------|
| New interface + 1 strategy impl | 2 new files | Low |
| `AssayType` enum + registry | 1 new file | Low |
| `ValidationConstants` refactor | 1 file | Low |
| `MeasurementPairMapper` update | 1 file | Low |
| `MeasurementPairService` update | 1 file | Medium |
| DTO schema migration (signal array) | 3 files | Medium |
| `CsvImportService` + `CsvImportConfig` | 2 files | Medium |
| `PdfReportService` + `ExcelExportService` | 2 files | Medium |
| Frontend (add_experiment, details, compare) | 3 files | High |
| Flyway migrations (V15–V17) | 3 SQL files | Medium |
| Unit + integration tests | ~8 test classes | High |
| **Total** | **~26 files** | **~3–4 sprint weeks** |

**Modules that require zero changes** (already assay-agnostic):
- `ValidationEngine` (reads only scalars)
- All curve fitter classes (`FourPLFitter`, etc.)
- `AuditLogService`, `AiInsight`, `Sample`, auth/security stack

### 4.1 Modules with minimal coupling (small refactor required)

> - `OutlierDetectionService` — currently reads `pair.getCvPct()`
>   directly. Requires replacing the field access with a call to
>   `strategy.computeCv(pair.getSignals())` (or equivalent) to become
>   fully assay-agnostic. Estimated effort: 2–3 hours.

---

## 5. Quick Wins Already Applied (Non-Breaking)

These changes were made as part of this analysis pass to reduce coupling
without structural refactoring:

| Change | File | What it does |
|--------|------|-------------|
| Added `ELISA_REPLICATE_COUNT = 2` constant | `ValidationConstants.java` | Names the magic number so it's visible as an assumption, not an arbitrary literal |
| Updated class Javadoc | `ValidationConstants.java` | References this document and the future multi-assay refactor |
| Updated field Javadoc | `MeasurementPair.java` | References this document and the future multi-assay refactor |

---

## 6. What NOT to do Until the Future Multi-Assay Refactor

Per CLAUDE.md constraints ("Multi-assay Preparation — Future"):

- Do **not** add `if (assayType == ELISA)` branches anywhere — this creates
  the very coupling we are trying to dissolve.
- Do **not** subclass `MeasurementPair` for PCR — use composition/strategy.
- Do **not** hardcode `"ELISA"`, `"PCR"`, or any assay name as a string
  literal in service or validation logic.
- Do **not** change `signal1`/`signal2` column names in the DB — they are
  part of the audit log field names for all existing rows.
