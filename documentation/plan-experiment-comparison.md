# Implementation Plan: Experiment Comparison Page

## Overview

A new Streamlit page (`pages/compare_experiments.py`) that allows a user to select two or more experiments and compare them side by side. The page is divided into collapsible sections that can be individually **locked open** to prevent accidental collapse. No new backend endpoints are required — all data is fetched via the existing `GET /api/experiments/{id}` endpoint.

---

## 1. Entry Points & Navigation

### 1.1 Dashboard (`app.py`)

Add a fifth navigation button — **"Compare Experiments"** — below the existing 2×2 grid, spanning the full width (or fitting into a new row).

```
[ Add Protocol ]      [ Search Protocols ]
[ Add Experiment ]    [ Search Experiments ]
[ Compare Experiments                      ]
```

Clicking the button calls `st.switch_page("pages/compare_experiments.py")`.

### 1.2 Search Experiments (`pages/search_experiments.py`)

Add a checkbox column to each result row (or a multi-select widget). When at least two experiments are checked, a **"Compare Selected"** button appears at the bottom of the results list.

- Clicking it stores the selected IDs in `st.session_state["compare_exp_ids"]` and calls `st.switch_page("pages/compare_experiments.py")`.
- Maximum of **4 experiments** selectable at once to keep the UI readable.

---

## 2. Session State Keys

| Key | Type | Description |
|-----|------|-------------|
| `compare_exp_ids` | `list[int]` | IDs of experiments to compare (2–4). Pre-populated when navigating from search. |
| `compare_data` | `dict[int, dict]` | Cached API responses keyed by experiment ID. |
| `lock_reagents` | `bool` | Whether the Reagent Lots section is locked open. |
| `lock_calibration` | `bool` | Whether the Calibration Pairs section is locked open. |
| `lock_controls` | `bool` | Whether the Control Pairs section is locked open. |
| `lock_chart` | `bool` | Whether the Curve Chart section is locked open. |

---

## 3. Page Structure

```
┌─────────────────────────────────────────────────────────────┐
│  ← Back to Search         Experiment Comparison             │
├─────────────────────────────────────────────────────────────┤
│  [Experiment selector]                                       │
│  Exp A: [dropdown/ID input]  Exp B: [dropdown/ID input]     │
│  [+ Add experiment]          [Load / Refresh]               │
├─────────────────────────────────────────────────────────────┤
│  ▼ Reagent Lots          [🔒 Lock]                          │
│    …                                                        │
├─────────────────────────────────────────────────────────────┤
│  ▼ Calibration Pairs     [🔒 Lock]                          │
│    …                                                        │
├─────────────────────────────────────────────────────────────┤
│  ▼ Control Pairs         [🔒 Lock]                          │
│    …                                                        │
├─────────────────────────────────────────────────────────────┤
│  ▼ Calibration Curve     [🔒 Lock]                          │
│    …                                                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Experiment Selector

- One **number input** (or text input with lookup) per experiment slot, labelled "Experiment A", "Experiment B", etc.
- An **"+ Add experiment"** link/button to add a third or fourth slot (up to 4 total).
- A **"Load"** button that fetches all selected IDs from the backend and stores results in `compare_data`. If an ID is not found (HTTP 404) or the call fails, an `st.error` is shown inline for that slot.
- Experiments from different protocols are allowed but a warning banner is shown: *"These experiments are from different protocols — comparison may not be meaningful."*
- When navigating from Search, the selector is pre-filled and data auto-loaded on page render.

---

## 5. Lockable Sections

Each section is rendered as follows:

```
┌─────────────────────────────────────────┐
│ Section Title                 [🔓 Lock] │  ← header row
│ ─────────────────────────────────────── │
│ <section content>                        │
└─────────────────────────────────────────┘
```

- The **Lock toggle** is a `st.toggle` (or `st.checkbox`) stored in session state (e.g., `lock_reagents`).
- When **unlocked**: the section is wrapped in `st.expander(expanded=True)` — the user can collapse it by clicking the expander header.
- When **locked**: the section is rendered in a plain `st.container()` with a visible divider and a border-style header — no collapse control is available. A subtle visual cue (e.g., a padlock icon in the title) indicates the locked state.
- All sections start **unlocked and expanded** on first load.

---

## 6. Section A — Reagent Lots

### Purpose
Verify that the same reagent lots (or intentionally different ones) were used across experiments.

### Data source
`ExperimentResponse.usedReagentBatches` → list of `UsedReagentBatchResponse` (`reagentName`, `lotNumber`, `expiryDate`).

### Display

Build a merged table with one row per unique `reagentName` across all compared experiments, and one column group per experiment:

| Reagent | Exp A — Lot | Exp A — Expiry | Exp B — Lot | Exp B — Expiry | … |
|---------|------------|----------------|------------|----------------|---|
| Anti-IgG | L2024-01 | 2027-06-01 | L2024-01 | 2027-06-01 | |
| TMB | L2025-03 | 2026-12-01 | **L2025-07** | 2026-12-01 | |

### Highlighting rules
- **Different lot numbers** for the same reagent → highlight those cells in amber (`#FFF3CD` background).
- **Reagent present in one experiment but missing in another** → show `—` (em-dash) in the missing cell, highlighted in red (`#F8D7DA`).
- **Same lot, same expiry** → no highlight (default table style).

### Implementation note
Use `pandas.DataFrame` with `pandas.Styler.apply()` to apply per-cell background colours. Render with `st.dataframe(..., use_container_width=True, hide_index=True)`.

---

## 7. Section B — Calibration Pairs

### Purpose
Compare the calibration curve replicates (OD readings, precision, accuracy) across experiments.

### Data source
`ExperimentResponse.measurementPairs` filtered by `pairType == "CALIBRATION"`, sorted by `concentrationNominal` ascending.

### Display

Render one sub-table per experiment, displayed in side-by-side `st.columns`. Column headers:

| Nominal Conc. | Signal 1 | Signal 2 | Mean | %CV | %Recovery | Outlier |

Below the tables, show a **summary row** per experiment (min/max/mean of `%CV` and `%Recovery`) to give a quick quality snapshot.

### Highlighting rules
- `%CV` cells that exceed the protocol's CV limit → flag with ⚠️ icon (protocol limit not currently in `ExperimentResponse`; use a configurable threshold, default **15%**, displayed as a note).
- `isOutlier == true` rows → amber row background.
- If the two experiments have a different number of calibration pairs, note this with an `st.warning` banner inside the section.

---

## 8. Section C — Control Pairs

### Purpose
Compare quality-control replicates across experiments.

### Data source
`ExperimentResponse.measurementPairs` filtered by `pairType == "CONTROL"`, sorted by `concentrationNominal` ascending.

### Display
Same column structure as Section B. Side-by-side `st.columns` with one table per experiment and a summary row.

### Highlighting rules
Same as Section B.

---

## 9. Section D — Calibration Curve Chart

### Purpose
Visually overlay the calibration curves of all compared experiments on a single plot to assess agreement, drift, or batch-to-batch variability.

### Data source
`pairType == "CALIBRATION"` points from each experiment, using `concentrationNominal` (X) and `signalMean` (Y). Outliers are plotted with a distinct marker shape (×) but not excluded from the line.

### Library
`plotly.graph_objects` (already a transitive dependency of Streamlit; add `plotly` to `frontend/requirements.txt` if not already present).

### Chart specification

- **Chart type**: Scatter + line (`mode="lines+markers"`).
- **X-axis**: Nominal Concentration (linear scale; log scale toggle via `st.radio`).
- **Y-axis**: Mean Signal (OD or RFU, linear).
- **One trace per experiment**, cycling through a fixed accessible colour palette (e.g., `#1f77b4`, `#ff7f0e`, `#2ca02c`, `#d62728`).
- **Legend**: shows experiment name and ID, e.g. `"Exp 12 — IgG Standard 2026-04-05"`.
- **Outlier points**: same colour as their trace, but marker symbol `x` and dashed line segment.
- **Hover tooltip**: Nominal Conc, Signal 1, Signal 2, Mean, %CV, %Recovery.
- **Layout**: white background, grid lines, responsive width via `use_container_width=True`.

```python
fig = go.Figure()
for exp in experiments:
    calibration_pts = [p for p in exp["measurementPairs"] if p["pairType"] == "CALIBRATION"]
    fig.add_trace(go.Scatter(
        x=[p["concentrationNominal"] for p in calibration_pts],
        y=[p["signalMean"] for p in calibration_pts],
        mode="lines+markers",
        name=f"Exp {exp['id']} — {exp['name']}",
        ...
    ))
st.plotly_chart(fig, use_container_width=True)
```

---

## 10. Files to Create / Modify

| Action | File | Notes |
|--------|------|-------|
| **Create** | `frontend/pages/compare_experiments.py` | New comparison page |
| **Modify** | `frontend/app.py` | Add "Compare Experiments" navigation button |
| **Modify** | `frontend/pages/search_experiments.py` | Add multi-select checkboxes + "Compare Selected" button |
| **Modify** | `frontend/requirements.txt` | Add `plotly` if not already listed |
| **Create** | `documentation/API-experiment-comparison.md` | Optional: document the comparison UX contract |

No backend changes are required. All data is obtained via the existing `GET /api/experiments/{id}` endpoint called once per selected experiment.

---

## 11. Backend — Optional Future Optimisation

If latency becomes a concern with 3–4 sequential GET calls, a bulk endpoint can be added later:

```
GET /api/experiments/bulk?ids=1,2,3
→ List<ExperimentResponse>
```

This is **out of scope for the initial implementation**.

---

## 12. Test Considerations

- **Unit tests** are not required for Streamlit pages (the existing test suite covers backend only).
- Manual test scenarios to verify:
  1. Two experiments from the same protocol, identical reagent lots → no highlights.
  2. Two experiments, one reagent lot different → amber highlight on differing row.
  3. One experiment missing a reagent → red highlight on missing cell.
  4. Experiments with a different number of calibration points → warning banner shown.
  5. Lock toggle keeps section visible after page interaction.
  6. Navigating from Search with pre-selected IDs auto-loads data.
  7. Invalid experiment ID shows inline error without breaking the rest of the page.

---

## 13. Assumptions & Open Questions

| # | Assumption / Question | Decision |
|---|----------------------|----------|
| 1 | `pairType` values in use are `CALIBRATION` and `CONTROL` only (no `SAMPLE` pairs yet). | Confirmed from entity definition. Add `SAMPLE` section later if needed. |
| 2 | CV threshold for highlighting is not available via API in `ExperimentResponse`. | Default to **15%**; make it a sidebar numeric input on the comparison page. |
| 3 | Plotly is acceptable as a charting library. | Preferred for interactive hover. Falls back to `st.line_chart` if not available. |
| 4 | Maximum 4 experiments per comparison. | Keeps UI readable; can be raised later. |
| 5 | "Lockable" means the section cannot be collapsed once locked, not that it is read-only. | Yes, purely a UI/collapse-prevention behaviour. |
