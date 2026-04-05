# Frontend Wireframes

Streamlit page structure and navigation for the EliSmart LIMS UI.

## Page Map

```
Dashboard (/)
  ├── Add Protocol (/add_protocol)
  ├── Add Reagent (/add_reagent)
  ├── Search Protocols (/search_protocols)  [low priority]
  └── Search Experiments (/search_experiments)
        └── Experiment Details (/experiment_details?id=...)
```

---

## 1. Dashboard `/`

**Entry point.** Calls `GET /api/health` on load to verify backend connection. Four call-to-action buttons arranged in a 2x2 grid.

```
+--------------------------------------------------------+
|  EliSmart LIMS                            [health ✅]   |
|                                                        |
|  Welcome to the dose-response assay manager.            |
|  Backend: RUNNING  |  Database: CONNECTED              |
|                                                        |
|  [ + Add Protocol ]      [ + Add Reagent ]             |
|                                                        |
|  [ 🔍 Search Protocols ] [ 📋 Search Experiments ]     |
|                                                        |
|  (last updated: 2026-04-06 00:00)                     |
+--------------------------------------------------------+
```

| Element       | Behavior                                    |
|---------------|---------------------------------------------|
| Health badge  | Calls `GET /api/health` on mount             |
| Add Protocol  | Navigates to `/add_protocol`                 |
| Add Reagent   | Navigates to `/add_reagent`                  |
| Search Protocols | Navigates to `/search_protocols` (deferred) |
| Search Experiments | Navigates to `/search_experiments`        |

---

## 2. Add Protocol `/add_protocol`

**Form page.** Collects protocol definition fields.

```
+--------------------------------------------------------+
|  ← Back to Dashboard                                   |
|                                                        |
|  New Protocol                                          |
|                                                        |
|  Name:              [_____________________________]    |
|  Description:       [_____________________________]    |
|                     [_____________________________]    |
|  Curve Type:        [ 4PL ▼ ]                          |
|  Min Recovery (%):  [___]  Max Recovery (%): [___]    |
|  Min CV (%):        [___]                              |
|                                                        |
|  Reagents Required:                                    |
|  [ + Add Reagent (dropdown from catalog) ]            |
|  [ X ] Anti-Human IgG - 1mg                          |
|  [ X ] Substrate TMB                                   |
|                                                        |
|                          [ Create Protocol ]           |
|                                                        |
|  (success / error message banner)                      |
+--------------------------------------------------------+
```

| Element       | Behavior                                    |
|---------------|---------------------------------------------|
| Curve type    | Dropdown: 4PL, 5PL, Linear                  |
| Reagent rows   | Adds/removes reagent IDs from a multi-select |
| Create button  | `POST /api/protocols`, shows result         |
| Back           | Navigates to `/`                            |

---

## 3. Add Reagent `/add_reagent`

**Form page.** Adds a reagent to the catalog.

```
+--------------------------------------------------------+
|  ← Back to Dashboard                                   |
|                                                        |
|  New Reagent                                           |
|                                                        |
|  Name:              [_____________________________]    |
|  Category:          [ Antibody ▼ ]                     |
|  Supplier/Prod ID:  [_____________________________]    |
|                                                        |
|                          [ Add Reagent ]               |
|                                                        |
|  (success / error message banner)                      |
+--------------------------------------------------------+
```

| Element       | Behavior                                    |
|---------------|---------------------------------------------|
| Category      | Dropdown from known categories              |
| Add button    | `POST /api/reagent-catalog`, shows result    |
| Back           | Navigates to `/`                            |

---

## 4. Search Protocols `/search_protocols` *(deferred)*

Filters on top, results table below. Low priority — skip for initial build.

```
+--------------------------------------------------------+
|  ← Back to Dashboard                                   |
|                                                        |
|  [Name filter]  [Category dropdown]  [ 🔍 Search ]     |
|                                                        |
|  ┌── Name ──────┬── Curve ───┬── Reagents ─┐           |
|  │ IgG Test      │ 4PL       │ 2           │ [View]    │
|  │ IgM Test      │ 5PL       │ 3           │ [View]    │
|  └──────────────┴───────────┴─────────────┘           |
+--------------------------------------------------------+
```

---

## 5. Search Experiments `/search_experiments`

Filters on top, paginated results table below. Each row has a **Details** button.

```
+--------------------------------------------------------+
|  ← Back to Dashboard                                   |
|                                                        |
|  [Name filter]  [Status ▼]  [Date from/to]  [Search]   |
|                                                        |
|  ┌── Name ───────┬── Date ──────┬── Status ─┐          |
|  │ Run 001       │ 2026-04-05   │ CO       │ [Details] │
|  │ Run 002       │ 2026-04-04   │ KO       │ [Details] │
|  │ Run 003       │ 2026-04-03   │ OK       │ [Details] │
|  └───────────────┴──────────────┴──────────┘          |
|          [ < Prev  1 of 3  Next > ]                    |
+--------------------------------------------------------+
```

| Element       | Behavior                                    |
|---------------|---------------------------------------------|
| Name filter   | Text input (searches by name)               |
| Status        | Dropdown: ALL, OK, KO, COMPLETED            |
| Date range    | Two date pickers                            |
| Search button | `POST /api/experiments/search`              |
| Details       | Navigates to `/experiment_details?id=X`     |

---

## 6. Experiment Details `/experiment_details?id=...`

Read-only view showing the full experiment, its measurement pairs, and used reagent batches.

```
+--------------------------------------------------------+
|  ← Back to Search                                      |
|                                                        |
|  Experiment: Run 001                    [Status: ✅ OK] |
|                                                        |
|  Protocol: IgG Test       Date: 2026-04-05 10:00       |
|  Operator: John Doe       Sample: Serum-42             |
|                                                        |
|  ── Measurement Pairs ─────────────────────────────    |
|  ┌── Name ───┬── Signal 1 ─┬── Signal 2 ─┬── Mean ─┬── %CV ─┬── %Recovery ─┐
|  │ Ctrl Low   │      0.120  │      0.119  │   0.119│   0.6 │      98.2%    │
|  │ Ctrl High  │      1.550  │      1.530  │   1.540│   0.9 │     101.5%    │
|  │ Sample A   │      0.890  │      0.880  │   0.885│   0.8 │      95.1%    │
|  └───────────┴────────────┴────────────┴────────┴──────┴───────────┘
|                                                        |
|  ── Used Reagent Batches ──────────────────────        |
|  Anti-Human IgG  |  LOT-2026-001   |  Exp: 2027-06-01  |
|  Substrate TMB    |  LOT-2025-045   |  Exp: 2026-12-01  |
+--------------------------------------------------------+
```

---

## Navigation Summary

```
  Dashboard
  ├── Add Protocol  ─────── POST /api/protocols
  ├── Add Reagent   ─────── POST /api/reagent-catalog
  ├── Search Protocols ──── GET  /api/protocols (deferred)
  └── Search Experiments ── POST /api/experiments/search
        └── Details ─────── GET  /api/experiments/{id}
```
