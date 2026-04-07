"""
Protocol Details page.

Read/write view of a single protocol.
- All fields are shown read-only by default.
- EDIT button (green, outlined) enables editing.
- DELETE button opens a confirmation dialog.
- Both operations are blocked server-side if experiments are linked to the protocol;
  a clear error message is shown to the user in that case.
The protocol ID is passed via st.session_state["selected_protocol_id"].
API: GET /api/protocols/{id}, PUT /api/protocols/{id}, DELETE /api/protocols/{id}
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import requests
import streamlit as st
from utils import resolve_backend_url

BACKEND_URL = resolve_backend_url()

protocol_id = st.session_state.get("selected_protocol_id")
if not protocol_id:
    st.warning("No protocol selected.")
    st.stop()


@st.dialog("Confirm Deletion")
def _confirm_delete(proto_name: str) -> None:
    """Modal dialog to confirm protocol deletion."""
    st.write(f"Delete protocol **{proto_name}**?")
    st.caption(
        "⚠️ This action cannot be undone. "
        "Deletion is blocked if experiments are linked to this protocol."
    )
    col_del, col_close = st.columns(2)
    with col_del:
        if st.button("Delete", type="primary", use_container_width=True):
            try:
                resp = requests.delete(f"{BACKEND_URL}/api/protocols/{protocol_id}", timeout=10)
                if resp.status_code == 204:
                    st.session_state.pop("selected_protocol_id", None)
                    st.session_state.pop("protocol_edit_mode", None)
                    st.switch_page("pages/search_protocols.py")
                else:
                    detail = resp.json().get("message", resp.text)
                    st.error(f"({resp.status_code}): {detail}")
            except requests.exceptions.RequestException as e:
                st.error(f"Request failed: {e}")
    with col_close:
        if st.button("Close", use_container_width=True):
            st.rerun()


# ---------------------------------------------------------------------------
# Load protocol data
# ---------------------------------------------------------------------------

try:
    resp = requests.get(f"{BACKEND_URL}/api/protocols/{protocol_id}", timeout=10)
    if resp.status_code != 200:
        st.error(f"Failed to load protocol (HTTP {resp.status_code})")
        st.stop()
    data = resp.json()
except requests.exceptions.RequestException as e:
    st.error(f"Request failed: {e}")
    st.stop()

# ---------------------------------------------------------------------------
# Header row: back | edit/cancel | delete
# ---------------------------------------------------------------------------

nav_col, edit_col, del_col = st.columns([5, 1, 1])
with nav_col:
    if st.button("← Back to Search"):
        st.session_state.pop("selected_protocol_id", None)
        st.session_state.pop("protocol_edit_mode", None)
        st.switch_page("pages/search_protocols.py")

edit_mode = st.session_state.get("protocol_edit_mode", False)

with edit_col:
    if not edit_mode:
        if st.button("✏️ Edit", use_container_width=True):
            st.session_state["protocol_edit_mode"] = True
            st.rerun()
    else:
        if st.button("Cancel", use_container_width=True):
            st.session_state["protocol_edit_mode"] = False
            st.rerun()

with del_col:
    st.markdown('<div class="delete-btn"></div>', unsafe_allow_html=True)
    if st.button("🗑️ Delete", use_container_width=True):
        _confirm_delete(data.get("name", str(protocol_id)))

st.title("Protocol Details")

st.info(
    "Edit and Delete are blocked by the server if experiments are linked to this protocol. "
    "Remove all linked experiments first."
)

st.markdown("---")

# ---------------------------------------------------------------------------
# Detail form (read-only or editable)
# ---------------------------------------------------------------------------

with st.form("protocol_form"):
    col1, col2 = st.columns(2)
    with col1:
        edit_name = st.text_input(
            "Name", value=data.get("name", ""), disabled=not edit_mode
        )
        edit_cal = st.number_input(
            "Calibration Pairs",
            value=int(data.get("numCalibrationPairs", 1)),
            min_value=1,
            step=1,
            disabled=not edit_mode,
        )
        edit_ctrl = st.number_input(
            "Control Pairs",
            value=int(data.get("numControlPairs", 1)),
            min_value=1,
            step=1,
            disabled=not edit_mode,
        )
    with col2:
        edit_cv = st.number_input(
            "Max %CV Allowed",
            value=float(data.get("maxCvAllowed", 0.0)),
            min_value=0.0,
            step=0.5,
            format="%.2f",
            disabled=not edit_mode,
        )
        edit_error = st.number_input(
            "Max %Error Allowed",
            value=float(data.get("maxErrorAllowed", 0.0)),
            min_value=0.0,
            step=0.5,
            format="%.2f",
            disabled=not edit_mode,
        )

    if edit_mode:
        saved = st.form_submit_button("Save Changes", type="primary", use_container_width=True)
    else:
        saved = False
        st.form_submit_button("(view only — click ✏️ Edit to modify)", disabled=True, use_container_width=True)

# ---------------------------------------------------------------------------
# Save logic
# ---------------------------------------------------------------------------

if saved:
    if not edit_name.strip():
        st.error("Name is required.")
    else:
        payload = {
            "name": edit_name.strip(),
            "numCalibrationPairs": int(edit_cal),
            "numControlPairs": int(edit_ctrl),
            "maxCvAllowed": float(edit_cv),
            "maxErrorAllowed": float(edit_error),
        }
        try:
            put_resp = requests.put(
                f"{BACKEND_URL}/api/protocols/{protocol_id}", json=payload, timeout=10
            )
            if put_resp.status_code == 200:
                st.success("Protocol updated successfully.")
                st.session_state["protocol_edit_mode"] = False
                data = put_resp.json()
                st.rerun()
            else:
                detail = put_resp.json().get("message", put_resp.text)
                st.error(f"Save failed ({put_resp.status_code}): {detail}")
        except requests.exceptions.RequestException as e:
            st.error(f"Request failed: {e}")
