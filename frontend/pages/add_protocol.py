"""
Add Protocol page.

Allows the user to define a new assay protocol: calibration/control pair counts,
CV and error limits, and the list of required reagents from the catalog.
New reagents can be added one at a time via the "Add reagent row" button and are
linked to the protocol automatically on submit.
Duplicate reagents (same name + manufacturer, case-insensitive) are rejected
with a warning before any API call is made.
API: GET /api/reagent-catalogs, POST /api/protocols,
     POST /api/reagent-catalogs (inline), POST /api/protocol-reagent-specs
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import requests
import streamlit as st
from utils import check_auth, resolve_backend_url

check_auth()
BACKEND_URL = resolve_backend_url()

if st.button("← Back to Dashboard"):
    st.switch_page("pages/dashboard.py")

st.title("New Protocol")
st.markdown("---")

# --- Load existing reagents from catalog (full data for duplicate check) ---
reagent_list: list[dict] = []
reagent_options: dict[int, str] = {}
try:
    resp = requests.get(f"{BACKEND_URL}/api/reagent-catalogs", params={"size": 1000}, timeout=10)
    if resp.status_code == 200:
        reagent_list = resp.json().get("content", [])
        for r in reagent_list:
            reagent_options[r["id"]] = f"{r['name']} ({r['manufacturer']})"
except requests.exceptions.RequestException:
    pass

# Pre-build a set of (name_lower, manufacturer_lower) for duplicate detection
existing_pairs: set[tuple[str, str]] = {
    (r["name"].lower(), r["manufacturer"].lower()) for r in reagent_list
}

# ---------------------------------------------------------------------------
# Protocol fields
# ---------------------------------------------------------------------------

st.subheader("Protocol Details")
name = st.text_input("Name", placeholder="e.g. IgG Test Protocol", key="proto_name")

# Curve type options: display label → enum value sent to the API
_CURVE_TYPE_OPTIONS: dict[str, str] = {
    "4PL — Four Parameter Logistic (ELISA standard)": "FOUR_PARAMETER_LOGISTIC",
    "5PL — Five Parameter Logistic (asymmetric)": "FIVE_PARAMETER_LOGISTIC",
    "3PL — Log-Logistic (minimum fixed at zero)": "LOG_LOGISTIC_3P",
    "Linear (y = mx + q)": "LINEAR",
    "Semi-log Linear (log X-axis)": "SEMI_LOG_LINEAR",
    "Point-to-Point (not recommended)": "POINT_TO_POINT",
}
curve_label = st.selectbox(
    "Curve Type",
    options=list(_CURVE_TYPE_OPTIONS.keys()),
    index=0,
    help="Mathematical model used to fit the calibration curve.",
    key="proto_curve",
)
curve_type = _CURVE_TYPE_OPTIONS[curve_label]

col1, col2, col3 = st.columns(3)
with col1:
    num_cal = st.number_input("Calibration Pairs", min_value=1, value=5, step=1, key="proto_cal")
with col2:
    num_ctrl = st.number_input("Control Pairs", min_value=1, value=3, step=1, key="proto_ctrl")
with col3:
    max_cv = st.number_input("Max CV (%)", min_value=0.0, step=0.5, value=10.0, format="%.1f", key="proto_cv")
max_error = st.number_input("Max Error Allowed (%)", min_value=0.0, step=0.5, value=15.0, format="%.1f", key="proto_err")

st.markdown("---")
st.subheader("Reagents")

# Section 1: select existing reagents
if reagent_options:
    st.markdown("**Select existing reagents from the catalog**")
    selected_labels = st.multiselect(
        "Existing reagents",
        options=list(reagent_options.values()),
        help="Select reagents already in the catalog",
        label_visibility="collapsed",
        key="proto_existing_reagents",
    )
else:
    st.info("No reagents in catalog yet. Use the form below to add new ones.")
    selected_labels = []

label_to_id = {v: k for k, v in reagent_options.items()}
selected_reagent_ids = [label_to_id[lbl] for lbl in selected_labels]

# Section 2: add new reagents one row at a time
st.markdown("**Add new reagents to the catalog**")
st.caption("Each new reagent will be created in the catalog and linked to this protocol.")

if "reagent_rows" not in st.session_state:
    st.session_state["reagent_rows"] = []
if "reagent_row_counter" not in st.session_state:
    st.session_state["reagent_row_counter"] = 0

if st.button("➕ Add reagent row", key="add_reagent_row"):
    st.session_state["reagent_row_counter"] += 1
    st.session_state["reagent_rows"].append(st.session_state["reagent_row_counter"])
    st.rerun()

rows: list[int] = st.session_state["reagent_rows"]

for row_id in rows:
    c1, c2, c3, c_del = st.columns([2, 2, 3, 0.5])
    with c1:
        st.text_input(f"Name #{row_id}", key=f"new_r_name_{row_id}", placeholder="e.g. Anti-Human IgA")
    with c2:
        st.text_input(f"Manufacturer #{row_id}", key=f"new_r_mfr_{row_id}", placeholder="e.g. Sigma-Aldrich")
    with c3:
        st.text_input(f"Description #{row_id} (optional)", key=f"new_r_desc_{row_id}")
    with c_del:
        st.markdown("&nbsp;", unsafe_allow_html=True)  # vertical alignment spacer
        st.markdown('<div class="delete-btn"></div>', unsafe_allow_html=True)
        if st.button("✕", key=f"del_row_{row_id}", help="Remove this reagent row"):
            st.session_state["reagent_rows"].remove(row_id)
            st.rerun()

st.markdown("---")

# ---------------------------------------------------------------------------
# Submit
# ---------------------------------------------------------------------------

if st.button("Create Protocol", type="primary", use_container_width=True, key="proto_submit"):
    if not name.strip():
        st.error("Protocol name is required.")
    else:
        # Client-side duplicate check for new reagents
        duplicate_found = False
        for row_id in rows:
            nm = st.session_state.get(f"new_r_name_{row_id}", "").strip()
            mfr = st.session_state.get(f"new_r_mfr_{row_id}", "").strip()
            if not nm or not mfr:
                continue
            if (nm.lower(), mfr.lower()) in existing_pairs:
                st.warning(
                    f"⚠️ A reagent named **{nm}** from **{mfr}** already exists in the catalog. "
                    "Remove this row or use 'Select existing reagents' instead."
                )
                duplicate_found = True

        if not duplicate_found:
            try:
                # 1. Create the protocol
                resp = requests.post(
                    f"{BACKEND_URL}/api/protocols",
                    json={
                        "name": name.strip(),
                        "curveType": curve_type,
                        "numCalibrationPairs": int(num_cal),
                        "numControlPairs": int(num_ctrl),
                        "maxCvAllowed": max_cv,
                        "maxErrorAllowed": max_error,
                    },
                    timeout=10,
                )
                if resp.status_code != 201:
                    detail = resp.json().get("message", resp.text)
                    st.error(f"Failed to create protocol ({resp.status_code}): {detail}")
                    st.stop()

                protocol_id = resp.json()["id"]
                st.success(f"Protocol created — ID {protocol_id}")

                # 2. Create any new reagents
                new_reagent_ids: list[int] = []
                for row_id in rows:
                    nm = st.session_state.get(f"new_r_name_{row_id}", "").strip()
                    mfr = st.session_state.get(f"new_r_mfr_{row_id}", "").strip()
                    if not nm or not mfr:
                        st.error(f"New reagent row {row_id} requires Name and Manufacturer.")
                        continue
                    r_resp = requests.post(
                        f"{BACKEND_URL}/api/reagent-catalogs",
                        json={
                            "name": nm,
                            "manufacturer": mfr,
                            "description": st.session_state.get(f"new_r_desc_{row_id}", "").strip(),
                        },
                        timeout=10,
                    )
                    if r_resp.status_code == 201:
                        rid = r_resp.json()["id"]
                        new_reagent_ids.append(rid)
                        st.info(f"New reagent added — ID {rid}: {nm}")
                    else:
                        rd = r_resp.json().get("message", r_resp.text)
                        st.error(f"Failed to add reagent '{nm}' ({r_resp.status_code}): {rd}")

                # 3. Link all selected + new reagents to the protocol
                for rid in selected_reagent_ids + new_reagent_ids:
                    s_resp = requests.post(
                        f"{BACKEND_URL}/api/protocol-reagent-specs",
                        json={"protocolId": protocol_id, "reagentId": rid, "isMandatory": True},
                        timeout=10,
                    )
                    if s_resp.status_code == 201:
                        st.success(f"Reagent {rid} linked to protocol")
                    else:
                        sd = s_resp.json().get("message", s_resp.text)
                        st.error(f"Failed to link reagent {rid} ({s_resp.status_code}): {sd}")

                # Reset new-reagent rows after successful submission
                st.session_state["reagent_rows"] = []
                st.session_state["reagent_row_counter"] = 0

            except requests.exceptions.RequestException as e:
                st.error(f"Request failed: {e}")
