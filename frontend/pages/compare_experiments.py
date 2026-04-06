"""
Compare Experiments page.

Side-by-side comparison of 2–4 experiments with four lockable sections:
  A) Reagent Lots — highlights lot differences and missing reagents
  B) Calibration Pairs — tabular view with per-column %CV flagging
  C) Control Pairs — tabular view with per-column %CV flagging
  D) Calibration Curve — interactive Plotly chart (linear or log X-axis)
Also embeds an AI analysis panel that calls the Gemini endpoint.
Can be reached directly or pre-loaded with IDs from the search page via
st.session_state["compare_exp_ids"].
API: GET /api/experiments/{id}, POST /api/ai/analyze
"""
import os
import base64
import requests
import streamlit as st
import pandas as pd
import plotly.graph_objects as go


# ---------------------------------------------------------------------------
# Bootstrap
# ---------------------------------------------------------------------------

def _resolve_backend_url():
    env = os.environ.get("BACKEND_URL")
    if env:
        return env
    try:
        return st.secrets.get("backend_url", "http://localhost:8080")
    except Exception:
        return "http://localhost:8080"


BACKEND_URL = _resolve_backend_url()
SLOT_LABELS = ["A", "B", "C", "D"]
MAX_SLOTS = 4
PALETTE = ["#1f77b4", "#ff7f0e", "#2ca02c", "#d62728"]


def _check_auth():
    if st.session_state.get("authenticated", False):
        return True
    st.stop()


_check_auth()

st.set_page_config(page_title="Compare Experiments", page_icon="📊", layout="wide")

LOGO_PATH = os.path.join(os.path.dirname(__file__), "..", "..", "assets", "EliSmartLogo.png")
if os.path.exists(LOGO_PATH):
    with open(LOGO_PATH, "rb") as f:
        logo_b64 = base64.b64encode(f.read()).decode()
    st.markdown(
        f'<div style="text-align:center; margin-bottom:0.5rem">'
        f'<img src="data:image/png;base64,{logo_b64}" style="max-width:200px; height:auto" />'
        f'</div>',
        unsafe_allow_html=True,
    )

with st.sidebar:
    st.caption(f"🔗 Backend: `{BACKEND_URL}`")
    if st.button("🚪 Logout", use_container_width=True):
        st.session_state["authenticated"] = False
        st.rerun()
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

st.session_state.setdefault("num_compare_slots", 2)
st.session_state.setdefault("compare_data", {})
st.session_state.setdefault("lock_reagents", False)
st.session_state.setdefault("lock_calibration", False)
st.session_state.setdefault("lock_controls", False)
st.session_state.setdefault("lock_chart", False)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _fetch_experiments(ids: list[int]) -> dict[int, dict]:
    """Fetch experiment data from the backend for a list of IDs.
    Returns a dict keyed by experiment ID. Errors are shown inline."""
    data: dict[int, dict] = {}
    for eid in ids:
        if eid == 0:
            continue
        try:
            resp = requests.get(f"{BACKEND_URL}/api/experiments/{eid}", timeout=10)
            if resp.status_code == 200:
                data[eid] = resp.json()
            elif resp.status_code == 404:
                st.error(f"Experiment {eid} not found (404).")
            else:
                st.error(f"Failed to load experiment {eid} (HTTP {resp.status_code}).")
        except requests.exceptions.RequestException as e:
            st.error(f"Request failed for experiment {eid}: {e}")
    return data


def _render_section(title: str, lock_key: str, render_fn, *args, **kwargs):
    """Renders a lockable section with a toggle button.

    When locked the section is displayed in a bordered container (no collapse
    control). When unlocked it is wrapped in an expander that the user can
    collapse manually.
    """
    locked = st.session_state.get(lock_key, False)

    h1, h2 = st.columns([7, 1])
    h1.subheader(("🔒 " if locked else "") + title)
    h2.toggle("Lock", key=lock_key, label_visibility="collapsed")

    # Read again after the toggle widget is rendered so the value is current
    locked = st.session_state.get(lock_key, False)

    if locked:
        with st.container(border=True):
            render_fn(*args, **kwargs)
    else:
        with st.expander(title, expanded=True):
            render_fn(*args, **kwargs)


# ---------------------------------------------------------------------------
# Section A — Reagent Lots
# ---------------------------------------------------------------------------

def _render_reagent_lots(experiments: list[dict]):
    # Collect all unique reagent names across all experiments
    all_reagents: set[str] = set()
    # Structure: {reagent_name: {exp_id: {"lot": str, "expiry": str}}}
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

    # Build DataFrame rows
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

    # Per-cell styling via Styler.apply(axis=None)
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
                    val = data.iloc[i][exp_col]
                    if val == "—":
                        styles.iloc[i, col_idx] = "background-color: #F8D7DA"

        return styles

    st.dataframe(df.style.apply(_style, axis=None), use_container_width=True, hide_index=True)
    st.caption(
        "🟡 Different lot number for same reagent   "
        "🔴 Reagent missing from one or more experiments"
    )


# ---------------------------------------------------------------------------
# Section B & C — Calibration / Control Pairs
# ---------------------------------------------------------------------------

def _render_pairs(experiments: list[dict], pair_type: str, cv_thr: float):
    pair_counts = []
    for exp in experiments:
        count = sum(
            1 for p in exp.get("measurementPairs", [])
            if p.get("pairType") == pair_type
        )
        pair_counts.append(count)

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

            def _style_pairs(data: pd.DataFrame) -> list[list[str]]:
                result = []
                for i in range(len(data)):
                    if outlier_flags[i]:
                        result.append(["background-color: #FFF3CD"] * len(data.columns))
                    else:
                        result.append([""] * len(data.columns))
                return result

            st.dataframe(
                pair_df.style.apply(_style_pairs, axis=None),
                use_container_width=True,
                hide_index=True,
            )

            # Summary row
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


# ---------------------------------------------------------------------------
# Section D — Calibration Curve Chart
# ---------------------------------------------------------------------------

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
                [[
                    p.get("signal1"), p.get("signal2"), p.get("signalMean"),
                    p.get("cvPct"), p.get("recoveryPct"),
                ] for p in pts],
            )

        if normal:
            xs, ys, cd = _pt(normal)
            fig.add_trace(go.Scatter(
                x=xs, y=ys,
                mode="lines+markers",
                name=label,
                line=dict(color=color, width=2),
                marker=dict(color=color, size=8),
                customdata=cd,
                hovertemplate=(
                    "<b>%{fullData.name}</b><br>"
                    "Conc: %{x}<br>"
                    "Signal 1: %{customdata[0]}<br>"
                    "Signal 2: %{customdata[1]}<br>"
                    "Mean: %{customdata[2]}<br>"
                    "%%CV: %{customdata[3]}<br>"
                    "%%Rec: %{customdata[4]}<extra></extra>"
                ),
            ))

        if outliers:
            xs, ys, cd = _pt(outliers)
            fig.add_trace(go.Scatter(
                x=xs, y=ys,
                mode="markers",
                name=f"{label} (outliers)",
                marker=dict(color=color, size=12, symbol="x", line=dict(width=2)),
                customdata=cd,
                hovertemplate=(
                    "<b>%{fullData.name} ⚠️ outlier</b><br>"
                    "Conc: %{x}<br>"
                    "Mean: %{customdata[2]}<extra></extra>"
                ),
            ))

    fig.update_layout(
        xaxis_title="Nominal Concentration",
        yaxis_title="Mean Signal",
        xaxis_type="log" if x_log else "linear",
        plot_bgcolor="white",
        paper_bgcolor="white",
        legend=dict(orientation="h", yanchor="bottom", y=1.02, xanchor="right", x=1),
        xaxis=dict(showgrid=True, gridcolor="#E5E5E5"),
        yaxis=dict(showgrid=True, gridcolor="#E5E5E5"),
        margin=dict(t=60),
    )

    st.plotly_chart(fig, use_container_width=True)


# ---------------------------------------------------------------------------
# Experiment Selector
# ---------------------------------------------------------------------------

pre_ids: list[int] = st.session_state.get("compare_exp_ids", [])
# Expand slot count to match the number of pre-filled IDs from the search page
if len(pre_ids) > st.session_state["num_compare_slots"]:
    st.session_state["num_compare_slots"] = min(len(pre_ids), MAX_SLOTS)
num_slots = st.session_state["num_compare_slots"]

st.subheader("Select Experiments")

id_cols = st.columns(min(num_slots, MAX_SLOTS))
raw_ids: list[int] = []
for i, col in enumerate(id_cols):
    with col:
        default = int(pre_ids[i]) if i < len(pre_ids) else 0
        val = st.number_input(
            f"Experiment {SLOT_LABELS[i]} — ID",
            min_value=0,
            value=default,
            step=1,
            key=f"cmp_id_{i}",
        )
        raw_ids.append(int(val))

btn_add, btn_load, btn_clear = st.columns([2, 3, 2])
with btn_add:
    if num_slots < MAX_SLOTS:
        if st.button("+ Add experiment"):
            st.session_state["num_compare_slots"] = num_slots + 1
            st.rerun()
    else:
        st.caption("Max 4 experiments")

with btn_load:
    load_clicked = st.button("Load / Refresh", type="primary", use_container_width=True)

with btn_clear:
    if st.button("Clear", use_container_width=True):
        st.session_state["compare_data"] = {}
        st.session_state["compare_exp_ids"] = []
        st.session_state["num_compare_slots"] = 2
        for k in ["lock_reagents", "lock_calibration", "lock_controls", "lock_chart"]:
            st.session_state[k] = False
        st.rerun()

# Auto-load when arriving from the search page with pre-selected IDs
auto_load = (
    bool(pre_ids)
    and not st.session_state["compare_data"]
    and not load_clicked
)

if load_clicked or auto_load:
    ids_to_fetch = [eid for eid in raw_ids if eid > 0]
    if len(ids_to_fetch) < 2:
        st.error("Select at least two experiment IDs.")
    else:
        with st.spinner("Loading experiments…"):
            fetched = _fetch_experiments(ids_to_fetch)
        st.session_state["compare_data"] = fetched
        # Clear the pre-filled ids so manual changes are not overridden on rerun
        st.session_state.pop("compare_exp_ids", None)

# ---------------------------------------------------------------------------
# Comparison Sections
# ---------------------------------------------------------------------------

compare_data: dict[int, dict] = st.session_state.get("compare_data", {})

if not compare_data:
    st.info("Enter experiment IDs above and click **Load / Refresh** to start comparing.")
    st.stop()

# Preserve the order the user entered
ordered_ids = [eid for eid in raw_ids if eid in compare_data]
# Fall back to any loaded IDs if the inputs were cleared
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
        "⚠️ These experiments are from **different protocols** — "
        "comparison may not be meaningful. Protocols: "
        + ", ".join(f"*{n}*" for n in protocol_names)
    )

# Experiment header cards
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
            (exp.get("date") or "").replace("T", " ")[:16]
            + f"  |  created by {exp.get('createdBy', '—')}"
        )

st.markdown("---")

# X-scale toggle for the chart (rendered here so it is available for the section callable)
x_log = st.radio(
    "Chart X-axis scale",
    options=["Linear", "Logarithmic"],
    horizontal=True,
    label_visibility="collapsed",
    index=0,
) == "Logarithmic"

st.markdown("")

_render_section(
    "Reagent Lots",
    "lock_reagents",
    _render_reagent_lots,
    experiments,
)

_render_section(
    "Calibration Pairs",
    "lock_calibration",
    _render_pairs,
    experiments,
    "CALIBRATION",
    cv_threshold,
)

_render_section(
    "Control Pairs",
    "lock_controls",
    _render_pairs,
    experiments,
    "CONTROL",
    cv_threshold,
)

_render_section(
    "Calibration Curve",
    "lock_chart",
    _render_curve,
    experiments,
    x_log,
)

# ---------------------------------------------------------------------------
# AI Analysis — Gemini
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
        height=120,
        key="gemini_question",
    )
    additional_info = st.text_area(
        "Additional context (optional)",
        placeholder="e.g. Reagent lot A1 was newly opened on 01/04. Lab temperature was 4°C above normal.",
        height=80,
        key="gemini_context",
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
                    st.error(
                        f"AI analysis failed (HTTP {resp.status_code}): {resp.text[:300]}"
                    )
            except requests.exceptions.RequestException as e:
                st.error(f"Request to backend failed: {e}")

if st.session_state.get("gemini_analysis"):
    st.markdown("**Analysis Result:**")
    st.info(st.session_state["gemini_analysis"])
