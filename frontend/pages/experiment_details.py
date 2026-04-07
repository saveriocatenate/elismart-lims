"""
Experiment Details page.

Read/write view of a single experiment.
- All fields except protocol and the set of linked reagents are editable.
- Reagent lot numbers and expiry dates are editable; which reagents are used is not.
- A Delete button opens a confirmation dialog; confirming calls DELETE and returns to search.
The experiment ID is passed via st.session_state["selected_exp_id"].
API: GET /api/experiments/{id}, PUT /api/experiments/{id}, DELETE /api/experiments/{id}
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import datetime
import requests
import streamlit as st
from utils import (
    check_auth, format_date, inject_global_css, render_logo, render_sidebar, resolve_backend_url,
)

BACKEND_URL = resolve_backend_url()

_STATUS_OPTIONS = ["PENDING", "COMPLETED", "OK", "KO", "VALIDATION_ERROR"]

check_auth()

st.set_page_config(page_title="Experiment Details", page_icon="🔬", layout="wide")

inject_global_css()

_ASSETS = os.path.join(os.path.dirname(__file__), "..", "..", "assets")
render_logo(_ASSETS)
render_sidebar(BACKEND_URL)

# ---------------------------------------------------------------------------
# Navigation + delete dialog
# ---------------------------------------------------------------------------

exp_id = st.session_state.get("selected_exp_id")
if not exp_id:
    st.warning("No experiment selected.")
    st.stop()


@st.dialog("Confirm Deletion")
def _confirm_delete(exp_name: str) -> None:
    """Modal dialog that asks the user to confirm experiment deletion."""
    st.write(f"Delete experiment **{exp_name}**? This action cannot be undone.")
    col_del, col_close = st.columns(2)
    with col_del:
        if st.button("Delete", type="primary", use_container_width=True):
            try:
                resp = requests.delete(f"{BACKEND_URL}/api/experiments/{exp_id}", timeout=10)
                if resp.status_code == 204:
                    del st.session_state["selected_exp_id"]
                    st.switch_page("pages/search_experiments.py")
                else:
                    detail = resp.json().get("message", resp.text)
                    st.error(f"Delete failed ({resp.status_code}): {detail}")
            except requests.exceptions.RequestException as e:
                st.error(f"Request failed: {e}")
    with col_close:
        if st.button("Close", use_container_width=True):
            st.rerun()


nav_col, del_col = st.columns([6, 1])
with nav_col:
    if st.button("← Back to Search"):
        del st.session_state["selected_exp_id"]
        st.switch_page("pages/search_experiments.py")

# ---------------------------------------------------------------------------
# Load experiment data
# ---------------------------------------------------------------------------

try:
    resp = requests.get(f"{BACKEND_URL}/api/experiments/{exp_id}", timeout=10)
    if resp.status_code != 200:
        st.error(f"Failed to load (HTTP {resp.status_code})")
        st.stop()
    data = resp.json()
except requests.exceptions.RequestException as e:
    st.error(f"Request failed: {e}")
    st.stop()

st.title("Experiment Details")

with del_col:
    st.markdown("&nbsp;", unsafe_allow_html=True)
    st.markdown('<div class="delete-btn"></div>', unsafe_allow_html=True)
    if st.button("🗑️ Delete", use_container_width=True, help="Delete this experiment"):
        _confirm_delete(data.get("name", str(exp_id)))

st.markdown("---")

# ---------------------------------------------------------------------------
# Editable metadata form
# ---------------------------------------------------------------------------

batches = data.get("usedReagentBatches", [])

with st.form("edit_form"):
    st.subheader("Experiment Details")

    col_name, col_status = st.columns([3, 1])
    with col_name:
        edit_name = st.text_input("Name", value=data.get("name", ""))
    with col_status:
        current_status = data.get("status", "OK")
        status_idx = _STATUS_OPTIONS.index(current_status) if current_status in _STATUS_OPTIONS else 0
        edit_status = st.selectbox("Status", _STATUS_OPTIONS, index=status_idx)

    col_date, col_proto = st.columns(2)
    with col_date:
        existing_date = None
        if data.get("date"):
            try:
                existing_date = datetime.date.fromisoformat(data["date"][:10])
            except ValueError:
                pass
        edit_date = st.date_input("Date", value=existing_date)

    with col_proto:
        st.text_input("Protocol (read-only)", value=data.get("protocolName", "—"), disabled=True)

    st.markdown("---")

    # Editable reagent batches (lot + expiry only; reagent name is read-only)
    st.subheader(f"Reagent Batches ({len(batches)} reagent{'s' if len(batches) != 1 else ''})")

    batch_lots: list[str] = []
    batch_expiries: list[datetime.date | None] = []
    for i, b in enumerate(batches):
        c1, c2, c3 = st.columns([3, 3, 2])
        c1.markdown(f"**{b.get('reagentName', '—')}**")
        lot = c2.text_input(
            "Lot Number",
            value=b.get("lotNumber", ""),
            key=f"edit_lot_{i}",
            label_visibility="collapsed",
        )
        existing_expiry = None
        if b.get("expiryDate"):
            try:
                existing_expiry = datetime.date.fromisoformat(b["expiryDate"])
            except ValueError:
                pass
        expiry = c3.date_input(
            "Expiry Date",
            value=existing_expiry,
            key=f"edit_expiry_{i}",
            label_visibility="collapsed",
        )
        batch_lots.append(lot)
        batch_expiries.append(expiry)

    st.markdown("---")
    saved = st.form_submit_button("Save Changes", type="primary", use_container_width=True)

# ---------------------------------------------------------------------------
# Save changes
# ---------------------------------------------------------------------------

if saved:
    if not edit_name.strip():
        st.error("Name is required.")
    else:
        reagent_batch_updates = [
            {
                "id": batches[i]["id"],
                "lotNumber": batch_lots[i].strip(),
                "expiryDate": batch_expiries[i].isoformat() if batch_expiries[i] else None,
            }
            for i in range(len(batches))
            if batch_lots[i].strip()  # skip if lot is empty (optional batches may have no lot)
        ]

        payload = {
            "name": edit_name.strip(),
            "date": datetime.datetime.combine(edit_date, datetime.time(0, 0)).isoformat(),
            "status": edit_status,
            "reagentBatchUpdates": reagent_batch_updates,
        }
        try:
            put_resp = requests.put(
                f"{BACKEND_URL}/api/experiments/{exp_id}", json=payload, timeout=10
            )
            if put_resp.status_code == 200:
                st.success("Changes saved successfully.")
                # Refresh local data with the updated response
                data = put_resp.json()
            else:
                detail = put_resp.json().get("message", put_resp.text)
                st.error(f"Save failed ({put_resp.status_code}): {detail}")
        except requests.exceptions.RequestException as e:
            st.error(f"Request failed: {e}")

# ---------------------------------------------------------------------------
# Read-only sections (measurement pairs)
# ---------------------------------------------------------------------------

pairs = data.get("measurementPairs", [])
st.subheader("Measurement Pairs", divider="gray")
if pairs:
    st.dataframe(
        [
            {
                "Type": p.get("pairType"),
                "Signal 1": p.get("signal1"),
                "Signal 2": p.get("signal2"),
                "Mean": p.get("signalMean"),
                "Nominal Conc.": p.get("concentrationNominal"),
                "%CV": p.get("cvPct"),
                "%Recovery": p.get("recoveryPct"),
                "Outlier": p.get("isOutlier"),
            }
            for p in pairs
        ],
        use_container_width=True,
        hide_index=True,
    )
else:
    st.info("No measurement pairs.")

# Audit metadata
st.markdown("---")
audit_cols = st.columns(3)
audit_cols[0].caption(f"Created by: {data.get('createdBy', '—')}")
audit_cols[1].caption(f"Created at: {format_date(data.get('createdAt'))}")
audit_cols[2].caption(f"Last updated: {format_date(data.get('updatedAt'))}")
