# Frontend Wireframes

Streamlit page structure and navigation for the EliSmart LIMS UI.

## Page Map

```
Dashboard (/)
  ├── Add Protocol (/add_protocol)
  ├── Add Reagent (/add_reagent)
  ├── Add Experiment (/add_experiment)
  ├── Search Protocols (/search_protocols)  [deferred]
  └── Search Experiments (/search_experiments)
        ├── Experiment Details (/experiment_details?id=...)
        └── Compare Experiments (/compare_experiments)
```

---

## 1. Dashboard `/`

**Entry point.** Calls `GET /api/health` on load to verify backend connection. Navigation buttons in a grid layout.

```
+--------------------------------------------------------+
|  EliSmart LIMS                            [health ✅]   |
|                                                        |
|  Welcome to the dose-response assay manager.           |
|  Backend: RUNNING  |  Database: CONNECTED              |
|                                                        |
|  [ + Add Protocol ]      [ 🧫 Add Reagent ]            |
|                                                        |
|  [ 🔍 Search Protocols ] [ 📋 Search Experiments ]     |
|                                                        |
|  [ 🔬 Add Experiment ]                                 |
|                                                        |
|  [ ⚖️ Compare Experiments ]                            |
+--------------------------------------------------------+
```

| Element              | Behavior                                          |
|----------------------|---------------------------------------------------|
| Health badge         | Calls `GET /api/health` on mount                  |
| Add Protocol         | Navigates to `/add_protocol`                      |
| Add Reagent          | Navigates to `/add_reagent`                       |
| Add Experiment       | Navigates to `/add_experiment`                    |
| Search Protocols     | Navigates to `/search_protocols` (deferred)       |
| Search Experiments   | Navigates to `/search_experiments`                |
| Compare Experiments  | Navigates to `/compare_experiments`               |

---

## 2. Add Protocol `/add_protocol`

**Form page.** Collects protocol definition fields, links existing reagents, and optionally creates new reagents inline.

```
+--------------------------------------------------------+
|  ← Back to Dashboard                                   |
|                                                        |
|  New Protocol                                          |
|                                                        |
|  Name:                   [_______________________]     |
|  Calibration Pairs: [5]  Control Pairs: [3]            |
|  Max CV (%): [10.0]      Max Error (%): [15.0]         |
|                                                        |
|  ── Reagents ──────────────────────────────────────    |
|  Select existing reagents: [ multi-select ▼ ]          |
|                                                        |
|  Add new reagents to catalog: [0 ▲▼]                   |
|  (rows expand per count)                               |
|                                                        |
|                       [ Create Protocol ]              |
+--------------------------------------------------------+
```

| Element              | Behavior                                                      |
|----------------------|---------------------------------------------------------------|
| Calibration/control  | Number inputs; drive the measurement-pair table in add_experiment |
| Reagents multi-select | Loaded from `GET /api/reagent-catalogs` (size=1000)          |
| New reagents rows    | Each row: Name + Manufacturer + Description (optional)        |
| Create button        | 1) `POST /api/protocols`  2) `POST /api/reagent-catalogs` for new ones  3) `POST /api/protocol-reagent-specs` for all |

---

## 3. Add Reagent `/add_reagent`

**Form page.** Adds a single reagent to the catalog.

```
+--------------------------------------------------------+
|  ← Back to Dashboard                                   |
|                                                        |
|  New Reagent                                           |
|                                                        |
|  Name:         [_____________________________]         |
|  Manufacturer: [_____________________________]         |
|  Description:  [_____________________________]         |
|                                                        |
|                          [ Add Reagent ]               |
+--------------------------------------------------------+
```

| Element    | Behavior                                        |
|------------|-------------------------------------------------|
| Add button | `POST /api/reagent-catalogs`, shows result      |

---

## 4. Add Experiment `/add_experiment`

**Form page.** Creates an experiment by selecting a protocol, filling reagent batches and measurement pairs.

```
+--------------------------------------------------------+
|  ← Back to Dashboard                                   |
|                                                        |
|  Protocol: [ ELISA IgG ▼ ]                             |
|  Protocol: 8 cal pairs, 3 ctrl pairs, max CV 10%       |
|                                                        |
|  ── Experiment Details ────────────────────────────    |
|  Name: [_____________]   Status: [ OK ▼ ]              |
|  Date: [2026-04-06]      Time: [09:00]                 |
|                                                        |
|  ── Reagent Batches (2 reagents) ──────────────────    |
|  Capture Ab *(mandatory)*  [Lot #_____] [Expiry ___]   |
|  Substrate TMB *(optional)* [Lot #_____] [Expiry ___]  |
|                                                        |
|  ── Calibration Pairs (8) ─────────────────────────    |
|  Conc. | Signal 1 | Signal 2 | Signal Mean             |
|  (8 rows)                                              |
|                                                        |
|  ── Control Pairs (3) ─────────────────────────────    |
|  Conc. | Signal 1 | Signal 2 | Signal Mean             |
|  (3 rows)                                              |
|                                                        |
|                       [ Create Experiment ]            |
+--------------------------------------------------------+
```

| Element           | Behavior                                                          |
|-------------------|-------------------------------------------------------------------|
| Protocol selector | Loads from `GET /api/protocols`; drives table sizes               |
| Reagent batches   | Loaded from `GET /api/protocol-reagent-specs?protocolId=X`; mandatory reagents require a lot number |
| Calibration rows  | Count = `numCalibrationPairs` from selected protocol              |
| Control rows      | Count = `numControlPairs` from selected protocol                  |
| Create button     | `POST /api/experiments` with full payload; redirects to Details   |

---

## 5. Search Protocols `/search_protocols` *(deferred)*

Filters on top, results table below.

```
+--------------------------------------------------------+
|  ← Back to Dashboard                                   |
|                                                        |
|  [Name filter]  [ 🔍 Search ]                          |
|                                                        |
|  Protocol search not yet implemented.                  |
+--------------------------------------------------------+
```

---

## 6. Search Experiments `/search_experiments`

Filters on top, paginated results table below. Each row has a **Details** button and a checkbox for comparison.

```
+--------------------------------------------------------+
|  ← Back to Dashboard                                   |
|                                                        |
|  [Name filter]  [Status ▼]  [Date]  [Date from/to]    |
|  [Page size ▼]                            [ Search ]   |
|                                                        |
|  ☐  Run 001   2026-04-05  IgG Test  ✅ OK    [Details] |
|  ☐  Run 002   2026-04-04  IgG Test  🔴 KO    [Details] |
|                                                        |
|  2 experiments selected (max 4)                        |
|  [ ⚖️ Compare Selected ]                               |
|                                                        |
|  [ < Prev   1 of 3   Next > ]                          |
+--------------------------------------------------------+
```

| Element           | Behavior                                                |
|-------------------|---------------------------------------------------------|
| Checkboxes        | Select up to 4 experiments for comparison               |
| Compare Selected  | Navigates to `/compare_experiments` with pre-filled IDs |
| Details button    | Navigates to `/experiment_details` with selected ID     |
| Pagination        | `POST /api/experiments/search` with page parameter      |

---

## 7. Experiment Details `/experiment_details`

Read-only view showing the full experiment, its measurement pairs, and used reagent batches.

```
+--------------------------------------------------------+
|  ← Back to Search                                      |
|                                                        |
|  [Status: OK]  Protocol: IgG Test  Date: 2026-04-05   |
|  Experiment: Run 001                                   |
|                                                        |
|  ── Measurement Pairs ─────────────────────────────    |
|  Type | Conc. | Sig 1 | Sig 2 | Mean | %CV | %Rec | Out|
|  ...                                                   |
|                                                        |
|  ── Reagent Batches Used ──────────────────────────    |
|  Reagent | Lot | Expiry                                |
|  ...                                                   |
+--------------------------------------------------------+
```

| Element  | Behavior                                              |
|----------|-------------------------------------------------------|
| Load     | `GET /api/experiments/{id}` via `st.session_state`    |

---

## 8. Compare Experiments `/compare_experiments`

Side-by-side comparison of 2–4 experiments with lockable sections and AI analysis.

```
+--------------------------------------------------------+
|  ← Back to Search     [CV threshold: 15.0%]           |
|                                                        |
|  Select Experiments                                    |
|  Exp A: [ID 1]  Exp B: [ID 2]  [+ Add experiment]     |
|                      [Load / Refresh]  [Clear]         |
|                                                        |
|  [Exp 1 card]  [Exp 2 card]                            |
|                                                        |
|  Chart X-axis: ◉ Linear  ○ Logarithmic                 |
|                                                        |
|  🔒 Reagent Lots        [Lock toggle]                  |
|  ┌─────────────────────────────────────────────────┐   |
|  │ Reagent | Exp 1 Lot | Exp 2 Lot                  │   |
|  └─────────────────────────────────────────────────┘   |
|                                                        |
|  🔒 Calibration Pairs  [Lock toggle]                   |
|  🔒 Control Pairs       [Lock toggle]                  |
|  🔒 Calibration Curve   [Lock toggle]                  |
|                                                        |
|  ── AI Analysis ───────────────────────────────────    |
|  [Your question for the AI analyst ...]                |
|  [Additional context (optional) ...]                   |
|  [ Analyze with Gemini AI ]                            |
+--------------------------------------------------------+
```

| Element          | Behavior                                                         |
|------------------|------------------------------------------------------------------|
| Experiment slots | Up to 4 slots; IDs pre-filled when arriving from search          |
| Load / Refresh   | Fetches `GET /api/experiments/{id}` for each valid ID            |
| Lock toggle      | Freezes the section in a bordered container; disables expander   |
| CV threshold     | Sidebar input; flags pairs with %CV above threshold with ⚠️      |
| Reagent table    | Yellow = lot number differs; red = reagent missing from an exp   |
| Curve chart      | Plotly scatter; outliers rendered as ✕ markers                   |
| AI Analysis      | `POST /api/ai/analyze` with current experiment IDs and question  |

---

## Navigation Summary

```
Dashboard
  ├── Add Protocol  ─────────── POST /api/protocols
  │                              POST /api/reagent-catalogs (inline new)
  │                              POST /api/protocol-reagent-specs
  ├── Add Reagent   ─────────── POST /api/reagent-catalogs
  ├── Add Experiment ─────────── GET  /api/protocols
  │                              GET  /api/protocol-reagent-specs
  │                              POST /api/experiments
  ├── Search Protocols ──────── (deferred)
  └── Search Experiments ────── POST /api/experiments/search
        ├── Details ──────────── GET  /api/experiments/{id}
        └── Compare ─────────── GET  /api/experiments/{id} (×N)
                                 POST /api/ai/analyze
```
