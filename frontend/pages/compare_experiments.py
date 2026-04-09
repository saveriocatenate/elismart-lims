"""
Compare Experiments page.

Side-by-side comparison of 2–4 experiments with four lockable sections:
  A) Reagent Lots — highlights lot differences and missing reagents
  B) Calibration Pairs — tabular view with per-column %CV flagging
  C) Control Pairs — tabular view with per-column %CV flagging
  D) Calibration Curve — interactive Plotly chart (linear or log X-axis)
Also embeds an AI analysis panel that calls the Gemini endpoint.

Experiment selection:
  - Experiments are selected via a search form (name filter) — no raw ID entry.
  - Up to 4 experiments can be added to the comparison queue.
  - Pre-selected experiments from the Search Experiments page are auto-loaded.
API: POST /api/experiments/search, GET /api/experiments/{id}, POST /api/ai/analyze
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import requests
import streamlit as st
import pandas as pd
import plotly.graph_objects as go

from utils import check_auth, format_date, resolve_backend_url

# ---------------------------------------------------------------------------
# Bootstrap
# ---------------------------------------------------------------------------

check_auth()
BACKEND_URL = resolve_backend_url()
MAX_SLOTS = 4
PALETTE = ["#1f77b4", "#ff7f0e", "#2ca02c", "#d62728"]

# CV threshold lives in the sidebar (page-specific)
with st.sidebar:
    st.markdown("---")
    st.markdown("**Settings**")
    cv_threshold = st.number_input(
        "CV threshold (%)",
        min_value=0.0,
        value=15.0,
        step=0.5,
        help="Pairs with %CV above this value are flagged with ⚠️",
    )

if st.button("← Back to Search"):
    st.switch_page("pages/search_experiments.py")

st.title("Experiment Comparison")
st.markdown("---")

# ---------------------------------------------------------------------------
# Session-state defaults
# ---------------------------------------------------------------------------

st.session_state.setdefault("compare_selected", [])   # list of {id, name, date, protocolName, protocolCurveType}
st.session_state.setdefault("compare_data", {})
st.session_state.setdefault("lock_reagents", False)
st.session_state.setdefault("lock_calibration", False)
st.session_state.setdefault("lock_controls", False)
st.session_state.setdefault("lock_chart", False)

# ---------------------------------------------------------------------------
# Auto-load pre-selected experiments from the Search page
# ---------------------------------------------------------------------------

pre_ids: list[int] = st.session_state.pop("compare_exp_ids", [])
if pre_ids:
    already_in = {s["id"] for s in st.session_state["compare_selected"]}
    for eid in pre_ids:
        if eid not in already_in and len(st.session_state["compare_selected"]) < MAX_SLOTS:
            try:
                r = requests.get(f"{BACKEND_URL}/api/experiments/{eid}", timeout=10)
                if r.status_code == 200:
                    d = r.json()
                    st.session_state["compare_selected"].append({
                        "id": eid,
                        "name": d.get("name", f"Exp {eid}"),
                        "date": d.get("date"),
                        "protocolName": d.get("protocolName", "—"),
                        "protocolCurveType": d.get("protocolCurveType", ""),
                    })
            except Exception:
                pass

# ---------------------------------------------------------------------------
# Selection section
# ---------------------------------------------------------------------------

st.subheader("Selected Experiments")

selected: list[dict] = st.session_state["compare_selected"]

_CURVE_DISPLAY = {
    "FOUR_PARAMETER_LOGISTIC": "4PL",
    "FIVE_PARAMETER_LOGISTIC": "5PL",
    "LOG_LOGISTIC_3P": "3PL",
    "LINEAR": "Linear",
    "SEMI_LOG_LINEAR": "Semi-log Linear",
    "POINT_TO_POINT": "Point-to-Point",
}

if selected:
    for item in list(selected):
        sc1, sc2, sc3, sc4, sc5 = st.columns([3, 2, 2, 1, 1])
        sc1.markdown(f"**{item['name']}**")
        sc2.caption(format_date(item.get("date")))
        sc3.caption(item.get("protocolName", "—"))
        curve_raw = item.get("protocolCurveType", "")
        sc4.caption(_CURVE_DISPLAY.get(curve_raw, curve_raw or "—"))
        if sc5.button("✕ Remove", key=f"rem_{item['id']}", use_container_width=True):
            st.session_state["compare_selected"] = [
                s for s in st.session_state["compare_selected"] if s["id"] != item["id"]
            ]
            # Clear loaded data so comparison reruns
            st.session_state["compare_data"] = {}
            st.rerun()
else:
    st.info("No experiments selected yet. Use the search below to add experiments.")

st.markdown("")

# Add experiment section
if len(selected) < MAX_SLOTS:
    with st.expander("➕ Add Experiment", expanded=not selected):
        with st.form("add_exp_form"):
            col_f1, col_f2 = st.columns([4, 1])
            with col_f1:
                add_name = st.text_input("Name contains", placeholder="Leave blank to search all")
            with col_f2:
                add_size = st.selectbox("Results", [5, 10, 20], index=0)
            add_searched = st.form_submit_button("Search", use_container_width=True)

        if add_searched:
            try:
                r = requests.post(
                    f"{BACKEND_URL}/api/experiments/search",
                    json={"name": add_name.strip() or None, "page": 0, "size": add_size},
                    timeout=10,
                )
                if r.status_code == 200:
                    st.session_state["add_exp_results"] = r.json().get("content", [])
                else:
                    st.error(f"Search failed (HTTP {r.status_code})")
                    st.session_state.pop("add_exp_results", None)
            except requests.exceptions.RequestException as e:
                st.error(f"Request failed: {e}")

        add_results = st.session_state.get("add_exp_results", [])
        if add_results:
            already_ids = {s["id"] for s in st.session_state["compare_selected"]}
            for exp in add_results:
                eid = exp["id"]
                ac1, ac2, ac3, ac3b, ac4 = st.columns([3, 2, 2, 1, 1])
                ac1.markdown(f"**{exp.get('name')}**")
                ac2.caption(format_date(exp.get("date")))
                ac3.caption(exp.get("protocolName", "—"))
                curve_raw = exp.get("protocolCurveType", "")
                ac3b.caption(_CURVE_DISPLAY.get(curve_raw, curve_raw or "—"))
                btn_label = "✓ Added" if eid in already_ids else "+ Add"
                btn_disabled = eid in already_ids or len(st.session_state["compare_selected"]) >= MAX_SLOTS
                if ac4.button(btn_label, key=f"add_{eid}", disabled=btn_disabled, use_container_width=True):
                    st.session_state["compare_selected"].append({
                        "id": eid,
                        "name": exp.get("name", f"Exp {eid}"),
                        "date": exp.get("date"),
                        "protocolName": exp.get("protocolName", "—"),
                        "protocolCurveType": exp.get("protocolCurveType", ""),
                    })
                    st.session_state["compare_data"] = {}
                    st.rerun()
else:
    st.caption("Maximum 4 experiments reached.")

# ---------------------------------------------------------------------------
# Load / Clear
# ---------------------------------------------------------------------------

btn_load, btn_clear = st.columns([3, 1])
with btn_load:
    load_clicked = st.button(
        "⚖️ Load Comparison",
        type="primary",
        use_container_width=True,
        disabled=len(selected) < 2,
    )
with btn_clear:
    if st.button("Clear All", use_container_width=True):
        st.session_state["compare_selected"] = []
        st.session_state["compare_data"] = {}
        st.session_state.pop("add_exp_results", None)
        for k in ["lock_reagents", "lock_calibration", "lock_controls", "lock_chart"]:
            st.session_state[k] = False
        st.rerun()

if load_clicked:
    if len(selected) < 2:
        st.error("Select at least 2 experiments.")
    else:
        ids_to_fetch = [s["id"] for s in selected]
        data_map: dict[int, dict] = {}
        with st.spinner("Loading experiments…"):
            for eid in ids_to_fetch:
                try:
                    r = requests.get(f"{BACKEND_URL}/api/experiments/{eid}", timeout=10)
                    if r.status_code == 200:
                        data_map[eid] = r.json()
                    elif r.status_code == 404:
                        st.error(f"Experiment {eid} not found.")
                    else:
                        st.error(f"Failed to load experiment {eid} (HTTP {r.status_code}).")
                except requests.exceptions.RequestException as e:
                    st.error(f"Request failed for experiment {eid}: {e}")
        st.session_state["compare_data"] = data_map

# ---------------------------------------------------------------------------
# Comparison helpers
# ---------------------------------------------------------------------------

def _render_section(title: str, lock_key: str, render_fn, *args, **kwargs):
    locked = st.session_state.get(lock_key, False)
    h1, h2 = st.columns([7, 1])
    h1.subheader(("🔒 " if locked else "") + title)
    h2.toggle("Lock", key=lock_key, label_visibility="collapsed")
    locked = st.session_state.get(lock_key, False)
    if locked:
        with st.container(border=True):
            render_fn(*args, **kwargs)
    else:
        with st.expander(title, expanded=True):
            render_fn(*args, **kwargs)


def _render_reagent_lots(experiments: list[dict]):
    all_reagents: set[str] = set()
    lookup: dict[str, dict[int, dict]] = {}
    for exp in experiments:
        for batch in exp.get("usedReagentBatches", []):
            name = batch.get("reagentName", "")
            all_reagents.add(name)
            lookup.setdefault(name, {})[exp["id"]] = {
                "lot": batch.get("lotNumber", "—"),
                "expiry": batch.get("expiryDate") or "—",
            }
    if not all_reagents:
        st.info("No reagent batches recorded for the selected experiments.")
        return
    rows = []
    for reagent in sorted(all_reagents):
        row: dict = {"Reagent": reagent}
        for exp in experiments:
            prefix = f"Exp {exp['id']}"
            info = lookup.get(reagent, {}).get(exp["id"])
            row[f"{prefix} — Lot"] = info["lot"] if info else "—"
            row[f"{prefix} — Expiry"] = info["expiry"] if info else "—"
        rows.append(row)
    df = pd.DataFrame(rows)

    def _style(data: pd.DataFrame) -> pd.DataFrame:
        styles = pd.DataFrame("", index=data.index, columns=data.columns)
        for i in range(len(data)):
            reagent = data.iloc[i]["Reagent"]
            lots = [
                lookup.get(reagent, {}).get(exp["id"], {}).get("lot", "—")
                for exp in experiments
            ]
            non_dash = [l for l in lots if l != "—"]
            lots_differ = len(set(non_dash)) > 1 if len(non_dash) > 1 else False
            for exp in experiments:
                prefix = f"Exp {exp['id']}"
                lot_col = f"{prefix} — Lot"
                exp_col = f"{prefix} — Expiry"
                if lot_col in data.columns:
                    col_idx = data.columns.get_loc(lot_col)
                    val = data.iloc[i][lot_col]
                    if val == "—":
                        styles.iloc[i, col_idx] = "background-color: #F8D7DA"
                    elif lots_differ:
                        styles.iloc[i, col_idx] = "background-color: #FFF3CD"
                if exp_col in data.columns:
                    col_idx = data.columns.get_loc(exp_col)
                    if data.iloc[i][exp_col] == "—":
                        styles.iloc[i, col_idx] = "background-color: #F8D7DA"
        return styles

    st.dataframe(df.style.apply(_style, axis=None), use_container_width=True, hide_index=True)
    st.caption(
        "🟡 Different lot number for same reagent   "
        "🔴 Reagent missing from one or more experiments"
    )


def _render_pairs(experiments: list[dict], pair_type: str, cv_thr: float):
    pair_counts = [
        sum(1 for p in exp.get("measurementPairs", []) if p.get("pairType") == pair_type)
        for exp in experiments
    ]
    if len(set(pair_counts)) > 1:
        st.warning(
            f"Experiments have different numbers of {pair_type.capitalize()} pairs: "
            + ", ".join(f"Exp {exp['id']}: {c}" for exp, c in zip(experiments, pair_counts))
        )
    cols = st.columns(len(experiments))
    for exp, col in zip(experiments, cols):
        with col:
            pairs = sorted(
                [p for p in exp.get("measurementPairs", []) if p.get("pairType") == pair_type],
                key=lambda p: (p.get("concentrationNominal") or 0),
            )
            st.caption(f"**Exp {exp['id']} — {exp['name']}**")
            if not pairs:
                st.info("No data")
                continue

            def _fmt(v):
                return f"{v:.4f}" if v is not None else "—"

            def _fmt2(v):
                return f"{v:.2f}" if v is not None else "—"

            rows = []
            for p in pairs:
                cv = p.get("cvPct")
                cv_flag = cv is not None and cv > cv_thr
                rows.append({
                    "Conc.": _fmt(p.get("concentrationNominal")),
                    "Sig 1": _fmt(p.get("signal1")),
                    "Sig 2": _fmt(p.get("signal2")),
                    "Mean": _fmt(p.get("signalMean")),
                    "%CV": ("⚠️ " if cv_flag else "") + _fmt2(cv),
                    "%Rec.": _fmt2(p.get("recoveryPct")),
                    "Out.": "⚠️" if p.get("isOutlier") else "",
                })
            pair_df = pd.DataFrame(rows)
            outlier_flags = [bool(p.get("isOutlier")) for p in pairs]

            def _style_pairs(data: pd.DataFrame) -> pd.DataFrame:
                styles = pd.DataFrame("", index=data.index, columns=data.columns)
                for i in range(len(data)):
                    if outlier_flags[i]:
                        styles.iloc[i, :] = "background-color: #FFF3CD"
                return styles

            st.dataframe(
                pair_df.style.apply(_style_pairs, axis=None),
                use_container_width=True,
                hide_index=True,
            )
            cv_vals = [p["cvPct"] for p in pairs if p.get("cvPct") is not None]
            rec_vals = [p["recoveryPct"] for p in pairs if p.get("recoveryPct") is not None]
            parts = []
            if cv_vals:
                parts.append(
                    f"**%CV** min {min(cv_vals):.1f} / max {max(cv_vals):.1f} / avg {sum(cv_vals)/len(cv_vals):.1f}"
                )
            if rec_vals:
                parts.append(
                    f"**%Rec** min {min(rec_vals):.1f} / max {max(rec_vals):.1f} / avg {sum(rec_vals)/len(rec_vals):.1f}"
                )
            if parts:
                st.caption("  |  ".join(parts))


def _render_curve(experiments: list[dict], x_log: bool):
    fig = go.Figure()
    for i, exp in enumerate(experiments):
        color = PALETTE[i % len(PALETTE)]
        label = f"Exp {exp['id']} — {exp['name']}"
        all_cal = sorted(
            [p for p in exp.get("measurementPairs", []) if p.get("pairType") == "CALIBRATION"],
            key=lambda p: (p.get("concentrationNominal") or 0),
        )
        normal = [p for p in all_cal if not p.get("isOutlier")]
        outliers = [p for p in all_cal if p.get("isOutlier")]

        def _pt(pts):
            return (
                [p.get("concentrationNominal") for p in pts],
                [p.get("signalMean") for p in pts],
                [[p.get("signal1"), p.get("signal2"), p.get("signalMean"),
                  p.get("cvPct"), p.get("recoveryPct")] for p in pts],
            )

        if normal:
            xs, ys, cd = _pt(normal)
            fig.add_trace(go.Scatter(
                x=xs, y=ys, mode="lines+markers", name=label,
                line=dict(color=color, width=2), marker=dict(color=color, size=8),
                customdata=cd,
                hovertemplate=(
                    "<b>%{fullData.name}</b><br>Conc: %{x}<br>"
                    "Signal 1: %{customdata[0]}<br>Signal 2: %{customdata[1]}<br>"
                    "Mean: %{customdata[2]}<br>%%CV: %{customdata[3]}<br>"
                    "%%Rec: %{customdata[4]}<extra></extra>"
                ),
            ))
        if outliers:
            xs, ys, cd = _pt(outliers)
            fig.add_trace(go.Scatter(
                x=xs, y=ys, mode="markers",
                name=f"{label} (outliers)",
                marker=dict(color=color, size=12, symbol="x", line=dict(width=2)),
                customdata=cd,
                hovertemplate=(
                    "<b>%{fullData.name} ⚠️ outlier</b><br>"
                    "Conc: %{x}<br>Mean: %{customdata[2]}<extra></extra>"
                ),
            ))
    fig.update_layout(
        xaxis_title="Nominal Concentration", yaxis_title="Mean Signal",
        xaxis_type="log" if x_log else "linear",
        plot_bgcolor="white", paper_bgcolor="white",
        legend=dict(orientation="h", yanchor="bottom", y=1.02, xanchor="right", x=1),
        xaxis=dict(showgrid=True, gridcolor="#E5E5E5"),
        yaxis=dict(showgrid=True, gridcolor="#E5E5E5"),
        margin=dict(t=60),
    )
    st.plotly_chart(fig, use_container_width=True)


# ---------------------------------------------------------------------------
# Comparison sections
# ---------------------------------------------------------------------------

compare_data: dict[int, dict] = st.session_state.get("compare_data", {})

if not compare_data:
    if len(selected) >= 2:
        st.info("Click **Load Comparison** above to start comparing.")
    st.stop()

ordered_ids = [s["id"] for s in selected if s["id"] in compare_data]
if not ordered_ids:
    ordered_ids = list(compare_data.keys())

experiments = [compare_data[eid] for eid in ordered_ids]

if len(experiments) < 2:
    st.warning("At least two experiments must load successfully to compare.")
    st.stop()

st.markdown("---")

# Protocol mismatch banner
protocol_names = list({exp.get("protocolName", "") for exp in experiments})
if len(protocol_names) > 1:
    st.warning(
        "⚠️ These experiments are from **different protocols** — comparison may not be meaningful. "
        "Protocols: " + ", ".join(f"*{n}*" for n in protocol_names)
    )

# Header cards
header_cols = st.columns(len(experiments))
for exp, col in zip(experiments, header_cols):
    status = exp.get("status", "")
    emoji = "✅" if status == "OK" else "🔴" if status == "KO" else "🟡"
    with col:
        st.metric(
            label=f"Exp {exp['id']} — {exp.get('protocolName', '—')}",
            value=exp.get("name", ""),
            delta=f"{emoji} {status}",
        )
        st.caption(
            format_date(exp.get("date")) + f"  |  created by {exp.get('createdBy', '—')}"
        )

st.markdown("---")

x_log = st.radio(
    "Chart X-axis scale", options=["Linear", "Logarithmic"],
    horizontal=True, label_visibility="collapsed", index=0,
) == "Logarithmic"

st.markdown("")

_render_section("Reagent Lots", "lock_reagents", _render_reagent_lots, experiments)
_render_section("Calibration Pairs", "lock_calibration", _render_pairs,
                experiments, "CALIBRATION", cv_threshold)
_render_section("Control Pairs", "lock_controls", _render_pairs,
                experiments, "CONTROL", cv_threshold)
_render_section("Calibration Curve", "lock_chart", _render_curve, experiments, x_log)

# ---------------------------------------------------------------------------
# AI Analysis
# ---------------------------------------------------------------------------

st.markdown("---")
st.subheader("AI Analysis")

with st.expander("Ask the AI Analyst", expanded=False):
    user_question = st.text_area(
        "Your question for the AI analyst",
        placeholder=(
            "e.g. Why did the low control fail in experiments 2 and 3? "
            "Is there a trend in IC50 values across runs?"
        ),
        height=120, key="gemini_question",
    )
    additional_info = st.text_area(
        "Additional context (optional)",
        placeholder="e.g. Reagent lot A1 was newly opened on 01/04. Lab temperature was 4°C above normal.",
        height=80, key="gemini_context",
    )
    run_analysis = st.button("Analyze with Gemini AI", type="primary", key="gemini_run")

if run_analysis:
    if not (st.session_state.get("gemini_question") or "").strip():
        st.warning("Please enter a question before running the analysis.")
    else:
        combined_question = st.session_state["gemini_question"]
        extra = (st.session_state.get("gemini_context") or "").strip()
        if extra:
            combined_question += "\n\nAdditional context: " + extra
        exp_ids = [exp["id"] for exp in experiments]
        with st.spinner("Gemini is analyzing the experiments…"):
            try:
                resp = requests.post(
                    f"{BACKEND_URL}/api/ai/analyze",
                    json={"experimentIds": exp_ids, "userQuestion": combined_question},
                    timeout=60,
                )
                if resp.status_code == 200:
                    st.session_state["gemini_analysis"] = resp.json().get("analysis", "")
                else:
                    st.error(f"AI analysis failed (HTTP {resp.status_code}): {resp.text[:300]}")
            except requests.exceptions.RequestException as e:
                st.error(f"Request to backend failed: {e}")

if st.session_state.get("gemini_analysis"):
    st.markdown("**Analysis Result:**")
    st.info(st.session_state["gemini_analysis"])
