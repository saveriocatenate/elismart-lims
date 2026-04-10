"""
Add Experiment page.

Creates a new experiment by selecting a protocol, filling in reagent batch lot numbers,
and entering calibration and control measurement pair values read from the ELISA machine.
Signal inputs are live-reactive: %CV is computed and colour-coded on every keystroke
without a form wrapper, so the server always recalculates all derived metrics at POST.
API: GET /api/protocols, GET /api/protocols/{id}, GET /api/protocol-reagent-specs,
     POST /api/experiments
"""
import math
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import datetime
import requests
import streamlit as st
from utils import check_auth, get_auth_headers, resolve_backend_url

check_auth()
BACKEND_URL = resolve_backend_url()

if st.button("← Back to Dashboard"):
    st.switch_page("pages/dashboard.py")

st.title("New Experiment")
st.markdown("---")


# ---------------------------------------------------------------------------
# Cached API helpers — avoid redundant fetches on every widget rerender
# ---------------------------------------------------------------------------

@st.cache_data(ttl=300, show_spinner=False)
def _load_protocols(backend: str, token: str) -> list:
    try:
        r = requests.get(
            f"{backend}/api/protocols",
            headers={"Authorization": f"Bearer {token}"},
            timeout=10,
        )
        return r.json() if r.status_code == 200 else []
    except requests.exceptions.RequestException:
        return []


@st.cache_data(ttl=60, show_spinner=False)
def _load_protocol_detail(backend: str, protocol_id: int, token: str) -> dict | None:
    try:
        r = requests.get(
            f"{backend}/api/protocols/{protocol_id}",
            headers={"Authorization": f"Bearer {token}"},
            timeout=10,
        )
        return r.json() if r.status_code == 200 else None
    except requests.exceptions.RequestException:
        return None


@st.cache_data(ttl=60, show_spinner=False)
def _load_reagent_specs(backend: str, protocol_id: int, token: str) -> list:
    try:
        r = requests.get(
            f"{backend}/api/protocol-reagent-specs",
            params={"protocolId": protocol_id},
            headers={"Authorization": f"Bearer {token}"},
            timeout=10,
        )
        return r.json() if r.status_code == 200 else []
    except requests.exceptions.RequestException:
        return []


# ---------------------------------------------------------------------------
# Live %CV helpers
# ---------------------------------------------------------------------------

def _compute_cv(s1: float, s2: float) -> float | None:
    """
    Compute %CV using the ISO 5725 / CLSI EP15-A3 formula for n=2:
    SD = |signal1 - signal2| / sqrt(2),  %CV = (SD / mean) * 100.
    Returns None when the mean is zero (division by zero).
    """
    mean = (s1 + s2) / 2.0
    if mean == 0.0:
        return None
    sd = abs(s1 - s2) / math.sqrt(2)
    return (sd / mean) * 100.0


def _cv_badge(s1: float, s2: float, max_cv: float | None) -> str:
    """
    Return an HTML badge string for the live %CV display.

    Colour coding (when max_cv is known):
      - green  if cv <= max_cv
      - yellow if cv <= max_cv * 1.5
      - red    if cv >  max_cv * 1.5

    When max_cv is None (no protocol selected), the badge is rendered in neutral grey.
    When both signals are 0, an empty string is returned (no-op default state).
    """
    if s1 == 0.0 and s2 == 0.0:
        return '<span style="color:#9E9E9E;font-size:0.85em">—</span>'

    cv = _compute_cv(s1, s2)
    if cv is None:
        return '<span style="color:#9E9E9E;font-size:0.85em">N/A</span>'

    if max_cv is None:
        color = "#607D8B"  # neutral blue-grey — no threshold known
    elif cv <= max_cv:
        color = "#2E7D32"  # green
    elif cv <= max_cv * 1.5:
        color = "#F9A825"  # amber
    else:
        color = "#C62828"  # red

    return (
        f'<span style="color:{color};font-weight:600;font-size:0.9em">'
        f'%CV: {cv:.1f}%'
        f'</span>'
    )


# ---------------------------------------------------------------------------
# Load protocol list
# ---------------------------------------------------------------------------

token = st.session_state.get("jwt_token", "")
protocols = _load_protocols(BACKEND_URL, token)

if not protocols:
    st.warning("No protocols found. Create a protocol first.")
    st.stop()

protocol_map = {p["id"]: p["name"] for p in protocols}

# Protocol selector — outside any form so its value can drive table sizes
selected_protocol_id = st.selectbox(
    "Protocol",
    options=list(protocol_map.keys()),
    format_func=lambda x: protocol_map[x],
    key="sel_protocol",
)

# Load protocol details and reagent specs (cached per protocol_id)
protocol_detail = _load_protocol_detail(BACKEND_URL, selected_protocol_id, token)
reagent_specs = _load_reagent_specs(BACKEND_URL, selected_protocol_id, token)

if not protocol_detail:
    st.error("Could not load protocol details.")
    st.stop()

num_cal = protocol_detail.get("numCalibrationPairs", 0)
num_ctrl = protocol_detail.get("numControlPairs", 0)
max_cv = protocol_detail.get("maxCvAllowed")  # None if key absent

st.caption(
    f"Protocol: **{protocol_detail['name']}** — "
    f"{num_cal} calibration pairs, {num_ctrl} control pairs, "
    f"max CV {max_cv}%, "
    f"max error {protocol_detail.get('maxErrorAllowed')}%"
)

st.markdown("---")

# ---------------------------------------------------------------------------
# Experiment metadata  (regular widgets — no form wrapper)
# ---------------------------------------------------------------------------

st.subheader("Experiment Details")
col_name, col_status = st.columns([3, 1])
with col_name:
    exp_name = st.text_input("Name", placeholder="e.g. IgG Run 2026-04-06", key="exp_name")
with col_status:
    exp_status = st.selectbox("Status", ["COMPLETED", "PENDING", "OK", "KO", "VALIDATION_ERROR"],
                               key="exp_status")
exp_date = st.date_input("Date", value=datetime.date.today(), key="exp_date")

st.markdown("---")

# ---------------------------------------------------------------------------
# Reagent Batches
# ---------------------------------------------------------------------------

st.subheader(f"Reagent Batches ({len(reagent_specs)} reagent{'s' if len(reagent_specs) != 1 else ''})")
if not reagent_specs:
    st.info("This protocol has no reagents defined.")

lot_numbers: list[str] = []
expiry_dates: list = []
reagent_ids: list[int] = []

for i, spec in enumerate(reagent_specs):
    mandatory_label = " *(mandatory)*" if spec.get("isMandatory") else " *(optional)*"
    c1, c2, c3 = st.columns([3, 3, 2])
    c1.markdown(f"**{spec['reagentName']}**{mandatory_label}")
    lot = c2.text_input(
        "Lot Number",
        key=f"lot_{i}",
        placeholder="e.g. LOT-2025-001",
        label_visibility="collapsed",
    )
    exp = c3.date_input(
        "Expiry Date",
        key=f"expiry_{i}",
        value=None,
        label_visibility="collapsed",
    )
    lot_numbers.append(lot)
    expiry_dates.append(exp)
    reagent_ids.append(spec["reagentId"])

st.markdown("---")

# ---------------------------------------------------------------------------
# Measurement pair rows — live %CV feedback
# Column layout: Signal 1 | Signal 2 | Live %CV | Conc. Nominal
# ---------------------------------------------------------------------------

_CV_LEGEND_HTML = (
    '<span style="font-size:0.8em;color:#9E9E9E">'
    'Signal 1 &nbsp;|&nbsp; Signal 2 &nbsp;|&nbsp; Live %%CV &nbsp;|&nbsp; Conc. Nominal'
    '</span>'
)


def _pair_row(prefix: str, idx: int, show_conc: bool = True) -> tuple:
    """
    Render one measurement-pair input row.

    Returns (signal1, signal2, conc_nominal).
    Displays the computed %CV badge inline after signal inputs.
    """
    c1, c2, c3, c4 = st.columns([2.5, 2.5, 2, 2])
    s1 = c1.number_input(
        "Signal 1", key=f"{prefix}_s1_{idx}", value=0.0,
        step=0.001, format="%.4f", label_visibility="collapsed",
    )
    s2 = c2.number_input(
        "Signal 2", key=f"{prefix}_s2_{idx}", value=0.0,
        step=0.001, format="%.4f", label_visibility="collapsed",
    )
    c3.markdown(_cv_badge(s1, s2, max_cv), unsafe_allow_html=True)
    conc = c4.number_input(
        "Conc. Nominal", key=f"{prefix}_conc_{idx}", value=0.0,
        step=0.001, format="%.4f", label_visibility="collapsed",
    ) if show_conc else 0.0
    return s1, s2, conc


# --- Calibration Pairs ---
st.subheader(f"Calibration Pairs ({num_cal})")
st.markdown(_CV_LEGEND_HTML, unsafe_allow_html=True)

cal_s1_vals: list[float] = []
cal_s2_vals: list[float] = []
cal_conc_vals: list[float] = []

for i in range(num_cal):
    s1, s2, conc = _pair_row("cal", i, show_conc=True)
    cal_s1_vals.append(s1)
    cal_s2_vals.append(s2)
    cal_conc_vals.append(conc)

st.markdown("---")

# --- Control Pairs ---
st.subheader(f"Control Pairs ({num_ctrl})")
st.markdown(_CV_LEGEND_HTML, unsafe_allow_html=True)

ctrl_s1_vals: list[float] = []
ctrl_s2_vals: list[float] = []
ctrl_conc_vals: list[float] = []

for i in range(num_ctrl):
    s1, s2, conc = _pair_row("ctrl", i, show_conc=True)
    ctrl_s1_vals.append(s1)
    ctrl_s2_vals.append(s2)
    ctrl_conc_vals.append(conc)

st.markdown("---")

# ---------------------------------------------------------------------------
# Submit
# ---------------------------------------------------------------------------

if st.button("Create Experiment", type="primary", use_container_width=True, key="btn_create"):

    # Validate name
    if not exp_name.strip():
        st.error("Experiment name is required.")
        st.stop()

    # Validate mandatory lot numbers
    missing = [
        reagent_specs[i]["reagentName"]
        for i in range(len(reagent_specs))
        if reagent_specs[i].get("isMandatory") and not lot_numbers[i].strip()
    ]
    if missing:
        st.error(f"Lot number is required for mandatory reagents: {', '.join(missing)}")
        st.stop()

    # Build usedReagentBatches (skip reagents with no lot number)
    used_batches = []
    for i, spec in enumerate(reagent_specs):
        lot = lot_numbers[i].strip()
        if not lot:
            continue
        entry: dict = {"reagentId": spec["reagentId"], "lotNumber": lot}
        if expiry_dates[i]:
            entry["expiryDate"] = expiry_dates[i].isoformat()
        used_batches.append(entry)

    # Build measurementPairs — server recalculates all derived fields (mean, %CV, recovery)
    def _build_pairs(pair_type: str, s1_list, s2_list, conc_list) -> list:
        return [
            {
                "pairType": pair_type,
                "concentrationNominal": c if c != 0.0 else None,
                "signal1": s1,
                "signal2": s2,
                "signalMean": None,
                "cvPct": None,
                "recoveryPct": None,
                "isOutlier": False,
            }
            for s1, s2, c in zip(s1_list, s2_list, conc_list)
        ]

    measurement_pairs = (
        _build_pairs("CALIBRATION", cal_s1_vals, cal_s2_vals, cal_conc_vals)
        + _build_pairs("CONTROL", ctrl_s1_vals, ctrl_s2_vals, ctrl_conc_vals)
    )

    exp_datetime = datetime.datetime.combine(exp_date, datetime.time(0, 0)).isoformat()

    payload = {
        "name": exp_name.strip(),
        "date": exp_datetime,
        "protocolId": selected_protocol_id,
        "status": exp_status,
        "usedReagentBatches": used_batches,
        "measurementPairs": measurement_pairs,
    }

    try:
        resp = requests.post(
            f"{BACKEND_URL}/api/experiments",
            json=payload,
            headers=get_auth_headers(),
            timeout=15,
        )
        if resp.status_code == 201:
            exp_id = resp.json().get("id")
            st.success(f"Experiment created — ID {exp_id}")
            st.session_state["selected_exp_id"] = exp_id
            if st.button("View Details"):
                st.switch_page("pages/experiment_details.py")
        else:
            detail = resp.json().get("message", resp.text)
            st.error(f"Failed to create experiment ({resp.status_code}): {detail}")
    except requests.exceptions.RequestException as e:
        st.error(f"Request failed: {e}")
