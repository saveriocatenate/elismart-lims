"""
Add Protocol page.

Allows the user to define a new assay protocol: calibration/control pair counts,
CV and error limits, and the list of required reagents from the catalog.
New reagents can be created inline and are linked to the protocol automatically.
API: GET /api/reagent-catalogs, POST /api/protocols,
     POST /api/reagent-catalogs (inline), POST /api/protocol-reagent-specs
"""
import os
import base64
import requests
import streamlit as st


def _resolve_backend_url():
    env = os.environ.get("BACKEND_URL")
    if env:
        return env
    try:
        return st.secrets.get("backend_url", "http://localhost:8080")
    except Exception:
        return "http://localhost:8080"

BACKEND_URL = _resolve_backend_url()


def _check_auth():
    if st.session_state.get("authenticated", False):
        return True
    st.stop()

_check_auth()

st.set_page_config(page_title="Add Protocol", page_icon="🧪", layout="wide")

# --- Shared header ---
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

# --- Page ---
if st.button("← Back to Dashboard"):
    st.switch_page("app.py")

st.title("New Protocol")
st.markdown("---")

# --- Load existing reagents ---
reagent_options = {}
try:
    resp = requests.get(f"{BACKEND_URL}/api/reagent-catalogs", params={"size": 1000}, timeout=10)
    if resp.status_code == 200:
        data = resp.json()
        for r in data.get("content", []):
            reagent_options[r["id"]] = f"{r['name']} ({r['manufacturer']})"
except requests.exceptions.RequestException:
    pass

with st.form("protocol_form"):
    name = st.text_input("Name", placeholder="e.g. IgG Test Protocol")
    col1, col2, col3 = st.columns(3)
    with col1:
        num_cal = st.number_input("Calibration Pairs", min_value=1, value=5, step=1)
    with col2:
        num_ctrl = st.number_input("Control Pairs", min_value=1, value=3, step=1)
    with col3:
        max_cv = st.number_input("Max CV (%)", min_value=0.0, step=0.5, value=10.0, format="%.1f")
    max_error = st.number_input("Max Error Allowed (%)", min_value=0.0, step=0.5, value=15.0, format="%.1f")

    st.markdown("---")
    st.subheader("Reagents")

    # Section 1: Select from existing
    if reagent_options:
        st.markdown("**Select existing reagents**")
        selected_indices = st.multiselect(
            "Reagents (hold Ctrl to select multiple)",
            options=list(reagent_options.values()),
            help="Select reagents already in the catalog",
        )
    else:
        st.info("No reagents in catalog. Use the form below to add new ones.")
        selected_indices = []

    # Map display labels back to IDs
    selected_reagent_ids = []
    label_to_id = {v: k for k, v in reagent_options.items()}
    for lbl in selected_indices:
        selected_reagent_ids.append(label_to_id[lbl])

    # Section 2: Add new reagents in a dynamic table
    st.markdown("**Add new reagents to the catalog**")
    n_new = st.number_input("Number of new reagents to add", min_value=0, value=0, step=1)
    new_reagent_names = []
    new_reagent_mfrs = []
    new_reagent_descs = []
    for i in range(int(n_new)):
        c1, c2 = st.columns([1, 1])
        with c1:
            new_reagent_names.append(
                st.text_input(f"Reagent {i+1} — Name", key=f"new_r_name_{i}", placeholder="e.g. Anti-Human IgA")
            )
        with c2:
            new_reagent_mfrs.append(
                st.text_input(f"Reagent {i+1} — Manufacturer", key=f"new_r_mfr_{i}", placeholder="e.g. Sigma-Aldrich")
            )
        desc = st.text_input(f"Reagent {i+1} — Description (optional)", key=f"new_r_desc_{i}")
        new_reagent_descs.append(desc)

    submitted = st.form_submit_button("Create Protocol", type="primary", use_container_width=True)
    if submitted:
        if not name.strip():
            st.error("Name is required.")
        else:
            try:
                # 1. Create the protocol
                protocol_payload = {
                    "name": name.strip(),
                    "numCalibrationPairs": num_cal,
                    "numControlPairs": num_ctrl,
                    "maxCvAllowed": max_cv,
                    "maxErrorAllowed": max_error,
                }
                resp = requests.post(f"{BACKEND_URL}/api/protocols", json=protocol_payload, timeout=10)
                if resp.status_code != 201:
                    detail = resp.json().get("message", resp.text)
                    st.error(f"Failed to create protocol ({resp.status_code}): {detail}")
                else:
                    protocol_id = resp.json()["id"]
                    st.success(f"Protocol created — ID {protocol_id}")

                    # 2. Create any new reagents
                    new_reagent_ids = []
                    for idx in range(int(n_new)):
                        nm = new_reagent_names[idx].strip()
                        mfr = new_reagent_mfrs[idx].strip()
                        if not nm or not mfr:
                            st.error(f"New reagent {idx+1} requires Name and Manufacturer.")
                            continue
                        r_payload = {
                            "name": nm,
                            "manufacturer": mfr,
                            "description": new_reagent_descs[idx].strip(),
                        }
                        r_resp = requests.post(f"{BACKEND_URL}/api/reagent-catalogs", json=r_payload, timeout=10)
                        if r_resp.status_code == 201:
                            rid = r_resp.json()["id"]
                            new_reagent_ids.append(rid)
                            st.info(f"New reagent added — ID {rid}: {nm}")
                        else:
                            rd = r_resp.json().get("detail", r_resp.text)
                            st.error(f"Failed to add reagent '{nm}' ({r_resp.status_code}): {rd}")

                    # 3. Bind all selected + new reagents to the protocol
                    all_reagent_ids = selected_reagent_ids + new_reagent_ids
                    for rid in all_reagent_ids:
                        spec_payload = {
                            "protocolId": protocol_id,
                            "reagentId": rid,
                            "isMandatory": True,
                        }
                        s_resp = requests.post(
                            f"{BACKEND_URL}/api/protocol-reagent-specs", json=spec_payload, timeout=10
                        )
                        if s_resp.status_code == 201:
                            st.success(f"Reagent {rid} linked to protocol")
                        else:
                            sd = s_resp.json().get("detail", s_resp.text)
                            st.error(f"Failed to link reagent {rid} ({s_resp.status_code}): {sd}")

            except requests.exceptions.RequestException as e:
                st.error(f"Request failed: {e}")
