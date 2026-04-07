"""
Add Experiment page.

Creates a new experiment by selecting a protocol, filling in reagent batch lot numbers,
and entering calibration and control measurement pair values read from the ELISA machine.
The number of calibration/control pair rows is driven by the selected protocol definition.
Mandatory reagents must have a lot number before submission.
API: GET /api/protocols, GET /api/protocols/{id}, GET /api/protocol-reagent-specs,
     POST /api/experiments
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import datetime
import requests
import streamlit as st
from utils import check_auth, inject_global_css, render_logo, render_sidebar, resolve_backend_url

BACKEND_URL = resolve_backend_url()

check_auth()

st.set_page_config(page_title="Add Experiment", page_icon="🔬", layout="wide")

inject_global_css()

_ASSETS = os.path.join(os.path.dirname(__file__), "..", "..", "assets")
render_logo(_ASSETS)
render_sidebar(BACKEND_URL)

if st.button("← Back to Dashboard"):
    st.switch_page("app.py")

st.title("New Experiment")
st.markdown("---")

# --- Load protocols ---
protocols = []
try:
    resp = requests.get(f"{BACKEND_URL}/api/protocols", timeout=10)
    if resp.status_code == 200:
        protocols = resp.json()
except requests.exceptions.RequestException:
    pass

if not protocols:
    st.warning("No protocols found. Create a protocol first.")
    st.stop()

protocol_map = {p["id"]: p["name"] for p in protocols}

# --- Protocol selector (outside form so its value can drive table sizes) ---
selected_protocol_id = st.selectbox(
    "Protocol",
    options=list(protocol_map.keys()),
    format_func=lambda x: protocol_map[x],
    key="sel_protocol",
)

# --- Fetch protocol details and reagent specs ---
protocol_detail = None
reagent_specs = []
try:
    pd_resp = requests.get(f"{BACKEND_URL}/api/protocols/{selected_protocol_id}", timeout=10)
    if pd_resp.status_code == 200:
        protocol_detail = pd_resp.json()
    spec_resp = requests.get(
        f"{BACKEND_URL}/api/protocol-reagent-specs",
        params={"protocolId": selected_protocol_id},
        timeout=10,
    )
    if spec_resp.status_code == 200:
        reagent_specs = spec_resp.json()
except requests.exceptions.RequestException as e:
    st.error(f"Failed to load protocol data: {e}")
    st.stop()

if not protocol_detail:
    st.error("Could not load protocol details.")
    st.stop()

num_cal = protocol_detail.get("numCalibrationPairs", 0)
num_ctrl = protocol_detail.get("numControlPairs", 0)

st.caption(
    f"Protocol: **{protocol_detail['name']}** — "
    f"{num_cal} calibration pairs, {num_ctrl} control pairs, "
    f"max CV {protocol_detail.get('maxCvAllowed')}%, "
    f"max error {protocol_detail.get('maxErrorAllowed')}%"
)

# --- Experiment form ---
with st.form("experiment_form"):

    # Metadata
    st.subheader("Experiment Details")
    col_name, col_status = st.columns([3, 1])
    with col_name:
        exp_name = st.text_input("Name", placeholder="e.g. IgG Run 2026-04-06")
    with col_status:
        exp_status = st.selectbox("Status", ["OK", "KO", "VALIDATION_ERROR"])
    exp_date = st.date_input("Date", value=datetime.date.today())

    st.markdown("---")

    # --- Reagent Batches table ---
    st.subheader(f"Reagent Batches ({len(reagent_specs)} reagent{'s' if len(reagent_specs) != 1 else ''})")
    if not reagent_specs:
        st.info("This protocol has no reagents defined.")

    lot_numbers = []
    expiry_dates = []
    reagent_ids = []
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

    # --- Calibration Pairs table ---
    st.subheader(f"Calibration Pairs ({num_cal})")
    st.caption("Signal 1 | Signal 2 | Signal Mean | Conc. Nominal")
    cal_concs, cal_s1, cal_s2, cal_sm = [], [], [], []
    for i in range(num_cal):
        c1, c2, c3, c4 = st.columns(4)
        cal_s1.append(
            c1.number_input("Signal 1", key=f"cal_s1_{i}", value=0.0, step=0.001,
                            format="%.4f", label_visibility="collapsed")
        )
        cal_s2.append(
            c2.number_input("Signal 2", key=f"cal_s2_{i}", value=0.0, step=0.001,
                            format="%.4f", label_visibility="collapsed")
        )
        cal_sm.append(
            c3.number_input("Signal Mean", key=f"cal_sm_{i}", value=0.0, step=0.001,
                            format="%.4f", label_visibility="collapsed")
        )
        cal_concs.append(
            c4.number_input("Conc. nominal", key=f"cal_conc_{i}", value=0.0, step=0.001,
                            format="%.4f", label_visibility="collapsed")
        )

    st.markdown("---")

    # --- Control Pairs table ---
    st.subheader(f"Control Pairs ({num_ctrl})")
    st.caption("Signal 1 | Signal 2 | Signal Mean | Conc. Nominal")
    ctrl_concs, ctrl_s1, ctrl_s2, ctrl_sm = [], [], [], []
    for i in range(num_ctrl):
        c1, c2, c3, c4 = st.columns(4)
        ctrl_s1.append(
            c1.number_input("Signal 1", key=f"ctrl_s1_{i}", value=0.0, step=0.001,
                            format="%.4f", label_visibility="collapsed")
        )
        ctrl_s2.append(
            c2.number_input("Signal 2", key=f"ctrl_s2_{i}", value=0.0, step=0.001,
                            format="%.4f", label_visibility="collapsed")
        )
        ctrl_sm.append(
            c3.number_input("Signal Mean", key=f"ctrl_sm_{i}", value=0.0, step=0.001,
                            format="%.4f", label_visibility="collapsed")
        )
        ctrl_concs.append(
            c4.number_input("Conc. nominal", key=f"ctrl_conc_{i}", value=0.0, step=0.001,
                            format="%.4f", label_visibility="collapsed")
        )

    st.markdown("---")
    submitted = st.form_submit_button("Create Experiment", type="primary", use_container_width=True)

# --- Submit logic ---
if submitted:
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
        entry = {"reagentId": spec["reagentId"], "lotNumber": lot}
        if expiry_dates[i]:
            entry["expiryDate"] = expiry_dates[i].isoformat()
        used_batches.append(entry)

    # Build measurementPairs — all values are read directly from the ELISA machine output
    def _pairs(pair_type, concs, s1_list, s2_list, sm_list):
        pairs = []
        for conc, s1, s2, sm in zip(concs, s1_list, s2_list, sm_list):
            pairs.append({
                "pairType": pair_type,
                "concentrationNominal": conc if conc != 0.0 else None,
                "signal1": s1,
                "signal2": s2,
                "signalMean": sm if sm != 0.0 else None,
                "cvPct": None,
                "recoveryPct": None,
                "isOutlier": False,
            })
        return pairs

    measurement_pairs = (
        _pairs("CALIBRATION", cal_concs, cal_s1, cal_s2, cal_sm)
        + _pairs("CONTROL", ctrl_concs, ctrl_s1, ctrl_s2, ctrl_sm)
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
        resp = requests.post(f"{BACKEND_URL}/api/experiments", json=payload, timeout=15)
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
