# Frontend Wireframes

Streamlit page structure and navigation for the EliSmart LIMS UI.

## Colour Palette

All pages share a global CSS palette defined in `frontend/utils.py`:

| Role | Colour | Usage |
|---|---|---|
| Add / Create / Submit | `#2E7D32` (dark green, `type="primary"`) | All add/create/submit buttons |
| Search / Navigate / Back | outlined `#2E7D32` (secondary) | All search, back, navigation buttons |
| Logout / Delete | `#C62828` (dark red) | Sidebar logout; future delete buttons |

Logout is automatically styled red because it is the only button rendered inside `[data-testid="stSidebar"]`.
Future delete buttons should be wrapped in `<div class="delete-btn">` to pick up the red style.

---

## Page Map

```
Dashboard (/)
  ├── Add Reagent (/add_reagent)
  ├── Add Protocol (/add_protocol)
  ├── Add Experiment (/add_experiment)
  ├── Search Reagents (/search_reagents)
  ├── Search Protocols (/search_protocols)
  │     └── Protocol Details (/protocol_details?id=...)
  ├── Search Experiments (/search_experiments)
  │     ├── Experiment Details (/experiment_details?id=...)
  │     └── Compare Experiments (/compare_experiments)
  └── [ADMIN only] User Management (/user_management)
```

---

## 1. Dashboard `/`

**Entry point.** Calls `GET /api/health` on load to verify backend connection. Navigation buttons in a two-column grid.

```
+--------------------------------------------------------+
|  EliSmart LIMS                            [health ✅]   |
|                                                        |
|  Welcome to the dose-response assay manager.           |
|                                                        |
|  LEFT COLUMN              RIGHT COLUMN                 |
|  [ 🧫 Add Reagent ]       [ 🔍 Search Reagents ]       |
|  [ ➕ Add Protocol ]       [ 🔍 Search Protocols ]      |
|  [ 🔬 Add Experiment ]     [ 📋 Search Experiments ]    |
|                                                        |
|  [ ⚖️ Compare Experiments ]  (full width)              |
|                                                        |
|  Sidebar: [🚪 Logout]                                  |
+--------------------------------------------------------+
```

| Element              | Behaviour                                         |
|----------------------|---------------------------------------------------|
| Health badge         | Calls `GET /api/health` on mount                  |
| Add Reagent          | Navigates to `/add_reagent`                       |
| Add Protocol         | Navigates to `/add_protocol`                      |
| Add Experiment       | Navigates to `/add_experiment`                    |
| Search Reagents      | Navigates to `/search_reagents`                   |
| Search Protocols     | Navigates to `/search_protocols`                  |
| Search Experiments   | Navigates to `/search_experiments`                |
| Compare Experiments  | Navigates to `/compare_experiments`               |

---

## 2. Add Protocol `/add_protocol`

**Form page.** Collects protocol definition fields, links existing reagents, and allows the user to add new catalog reagents one row at a time via the "Add reagent row" button.

```
+--------------------------------------------------------+
|  ← Back to Dashboard                                   |
|                                                        |
|  New Protocol                                          |
|                                                        |
|  Name:                   [_______________________]     |
|  Curve Type: [ 4PL — Four Parameter Logistic ▼ ]       |
|  Calibration Pairs: [5]  Control Pairs: [3]            |
|  Max CV (%): [10.0]      Max Error (%): [15.0]         |
|                                                        |
|  ── Reagents ──────────────────────────────────────    |
|  Select existing reagents: [ multi-select ▼ ]          |
|                                                        |
|  Add new reagents to catalog:                          |
|  [ ➕ Add reagent row ]                                |
|  (each click adds one row: Name | Manufacturer | Desc) |
|                                                        |
|                       [ Create Protocol ]              |
+--------------------------------------------------------+
```

| Element                | Behaviour                                                          |
|------------------------|--------------------------------------------------------------------|
| Curve Type selector    | Selectbox with 6 options (4PL, 5PL, 3PL, Linear, Semi-log, P2P); default 4PL |
| Calibration/control    | Number inputs; drive the measurement-pair rows in add_experiment   |
| Reagents multi-select  | Loaded from `GET /api/reagent-catalogs` (size=1000)               |
| Add reagent row button | Increments session-state counter; renders one extra input row      |
| New reagent row        | Name + Manufacturer + Description (optional)                       |
| Create button          | 1) `POST /api/protocols`  2) `POST /api/reagent-catalogs` for each new row  3) `POST /api/protocol-reagent-specs` for all |

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

| Element    | Behaviour                                       |
|------------|-------------------------------------------------|
| Add button | `POST /api/reagent-catalogs`, shows result      |

---

## 4. Add Experiment `/add_experiment`

**Form page.** Creates an experiment by selecting a protocol, filling reagent batches and measurement pairs. All date/time fields use native calendar and time pickers — no ISO format entry required.

```
+--------------------------------------------------------+
|  ← Back to Dashboard                                   |
|                                                        |
|  Protocol: [ ELISA IgG ▼ ]                             |
|  Protocol: 8 cal pairs, 3 ctrl pairs, max CV 10%       |
|                                                        |
|  ── Experiment Details ────────────────────────────    |
|  Name: [_____________]   Status: [ OK ▼ ]              |
|  Date: [📅 calendar]     Time: [⏰ picker]             |
|                                                        |
|  ── Reagent Batches (2 reagents) ──────────────────    |
|  Capture Ab *(mandatory)*  [Lot #_____] [📅 Expiry]    |
|  Substrate TMB *(optional)* [Lot #_____] [📅 Expiry]   |
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

| Element           | Behaviour                                                         |
|-------------------|-------------------------------------------------------------------|
| Protocol selector | Loads from `GET /api/protocols`; drives table sizes               |
| Date/time fields  | `st.date_input` / `st.time_input` — calendar/clock pickers        |
| Reagent batches   | Loaded from `GET /api/protocol-reagent-specs?protocolId=X`; mandatory reagents require a lot number |
| Expiry date       | `st.date_input` with `value=None` (optional)                      |
| Calibration rows  | Count = `numCalibrationPairs` from selected protocol              |
| Control rows      | Count = `numControlPairs` from selected protocol                  |
| Create button     | `POST /api/experiments` with full payload; shows success + ID     |

---

## 5. Search Reagents `/search_reagents`

Search the reagent catalog by name and/or manufacturer with pagination.

```
+--------------------------------------------------------+
|  ← Back to Dashboard                                   |
|                                                        |
|  [Name filter]  [Manufacturer filter]  [Page size ▼]  |
|                                         [ Search ]     |
|                                                        |
|  ID | Name               | Manufacturer  | Description |
|  1  | Anti-IgG Antibody  | Sigma-Aldrich | ...         |
|                                                        |
|  [ ← Prev ]                              [ Next → ]   |
+--------------------------------------------------------+
```

| Element            | Behaviour                                              |
|--------------------|--------------------------------------------------------|
| Name filter        | Partial match, case-insensitive                        |
| Manufacturer filter| Partial match, case-insensitive                        |
| Search button      | `GET /api/reagent-catalogs` with filters and pagination |
| Pagination         | Controlled via page parameter                          |

---

## 6. Search Protocols `/search_protocols`

Search protocols by partial name match with pagination.

```
+--------------------------------------------------------+
|  ← Back to Dashboard                                   |
|                                                        |
|  [Name filter]  [Page size ▼]          [ Search ]      |
|                                                        |
|  ID | Name       | Curve Type | Cal. Pairs | Ctrl Pairs|
|  1  | IgG Test   | 4PL        | 7          | 3         |
|                                                        |
|  [ ← Prev ]                              [ Next → ]   |
+--------------------------------------------------------+
```

| Element       | Behaviour                                                |
|---------------|----------------------------------------------------------|
| Name filter   | Partial match, case-insensitive                          |
| Search button | `GET /api/protocols/search` with name filter and pagination |
| Pagination    | Controlled via page parameter                            |

---

## 7. Search Experiments `/search_experiments`

Filters on top (including calendar date pickers), paginated results table below. Each row has a **Details** button and a checkbox for comparison.

```
+--------------------------------------------------------+
|  ← Back to Dashboard                                   |
|                                                        |
|  [Name filter]  [Status ▼]  [Page size ▼]              |
|  [📅 Date]  [📅 Date from]  [📅 Date to]  [ Search ]   |
|                                                        |
|  ☐  Run 001   2026-04-05  IgG Test  ✅ OK    [Details] |
|  ☐  Run 002   2026-04-04  IgG Test  🔴 KO    [Details] |
|                                                        |
|  2 experiments selected (max 4)                        |
|  [ ⚖️ Compare Selected ]                               |
|                                                        |
|  [ ← Prev ]                              [ Next → ]   |
+--------------------------------------------------------+
```

| Element           | Behaviour                                                |
|-------------------|----------------------------------------------------------|
| Date pickers      | `st.date_input` with `value=None`; converted to ISO on submit |
| Checkboxes        | Select up to 4 experiments for comparison                |
| Compare Selected  | Navigates to `/compare_experiments` with pre-filled IDs  |
| Details button    | Navigates to `/experiment_details` with selected ID      |
| Pagination        | `POST /api/experiments/search` with page parameter       |

---

## 8. Experiment Details `/experiment_details`

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

| Element  | Behaviour                                              |
|----------|--------------------------------------------------------|
| Load     | `GET /api/experiments/{id}` via `st.session_state`     |

---

## 9. Compare Experiments `/compare_experiments`

Side-by-side comparison of 2–4 experiments with lockable sections and AI analysis.

```
+--------------------------------------------------------+
|  ← Back to Search     [CV threshold: 15.0%] (sidebar) |
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

| Element          | Behaviour                                                         |
|------------------|-------------------------------------------------------------------|
| Experiment slots | Up to 4 slots; IDs pre-filled when arriving from search           |
| Load / Refresh   | Fetches `GET /api/experiments/{id}` for each valid ID             |
| Lock toggle      | Freezes the section in a bordered container; disables expander    |
| CV threshold     | Sidebar input; flags pairs with %CV above threshold with ⚠️       |
| Reagent table    | Yellow = lot number differs; red = reagent missing from an exp    |
| Curve chart      | Plotly scatter; outliers rendered as ✕ markers                    |
| AI Analysis      | `POST /api/ai/analyze` with current experiment IDs and question   |

---

---

## 10. Protocol Details `/protocol_details`

Read/write view of a single protocol. Accessed from Search Protocols → Details button.
Displays protocol metadata and the linked reagent spec list.

```
+--------------------------------------------------------+
|  ← Back to Search                        [✏️ Edit]    |
|                                                        |
|  Protocol: IgG Test Protocol                           |
|  Curve Type: 4PL  | Cal. Pairs: 7 | Ctrl Pairs: 3     |
|  Max %CV: 10%     | Max %Error: 15%                    |
|                                                        |
|  ── Reagents Required ──────────────────────────────   |
|  🧪 Anti-IgG Antibody      [obbligatorio]              |
|  🧪 TMB Substrate           [opzionale]                |
+--------------------------------------------------------+
```

| Element     | Behaviour                                           |
|-------------|-----------------------------------------------------|
| Load        | `GET /api/protocols/{id}` via `selected_protocol_id` |
| Edit button | Toggles edit mode (green banner)                    |
| Save        | `PUT /api/protocols/{id}`                           |
| Delete      | `DELETE /api/protocols/{id}` with confirmation dialog (ADMIN only) |

---

## 11. User Management `/user_management`

**Admin-only page.** Manage registered users: view role, promote/demote, delete.
Visible in navigation only for ADMIN role.

```
+--------------------------------------------------------+
|  User Management                                       |
|                                                        |
|  ID | Username   | Role      | Actions                 |
|  1  | admin      | ADMIN     | [—]                     |
|  2  | analyst1   | ANALYST   | [Change Role] [Delete]  |
|  3  | reviewer   | REVIEWER  | [Change Role] [Delete]  |
|                                                        |
|  ── Add New User ──────────────────────────────────    |
|  Username: [___]  Password: [___]  Role: [ANALYST ▼]  |
|                          [ Register User ]             |
+--------------------------------------------------------+
```

| Element      | Behaviour                                               |
|--------------|---------------------------------------------------------|
| Load         | `GET /api/users`                                        |
| Change Role  | `PUT /api/users/{id}/role` with role dropdown           |
| Delete       | `DELETE /api/users/{id}` with confirmation dialog       |
| Register     | `POST /api/auth/register` with username, password, role |

---

## Navigation Summary

```
Dashboard
  ├── Add Reagent    ─────────── POST /api/reagent-catalogs
  ├── Add Protocol   ─────────── POST /api/protocols
  │                               POST /api/reagent-catalogs (inline new)
  │                               POST /api/protocol-reagent-specs
  ├── Add Experiment ─────────── GET  /api/protocols
  │                               GET  /api/protocol-reagent-specs
  │                               POST /api/experiments
  │                               POST /api/experiments/{id}/import-csv
  ├── Search Reagents ──────────  GET  /api/reagent-catalogs (paged)
  ├── Search Protocols ─────────  GET  /api/protocols/search (paged)
  │     └── Protocol Details ───  GET  /api/protocols/{id}
  │                               PUT  /api/protocols/{id}
  ├── Search Experiments ──────  POST /api/experiments/search
  │     ├── Details ─────────────  GET  /api/experiments/{id}
  │     │                          PUT  /api/experiments/{id}
  │     │                          POST /api/experiments/{id}/validate
  │     │                          POST /api/ai/analyze
  │     └── Compare ─────────────  GET  /api/experiments/{id} (×N)
  │                                POST /api/ai/analyze
  └── [ADMIN] User Management ──  GET  /api/users
                                   PUT  /api/users/{id}/role
                                   DELETE /api/users/{id}
                                   POST /api/auth/register
```
