"""
Experiment Details page.

Read/write view of a single experiment.
- Default view: all fields shown read-only (click ✏️ Edit to enable editing).
- EDIT button (green outlined) enables editing of metadata, reagent batches, and
  measurement pair signal values. The total number of pairs cannot change.
- DELETE button opens a confirmation dialog; confirming calls DELETE and returns to search.
The experiment ID is passed via st.session_state["selected_exp_id"].
API: GET /api/experiments/{id}, PUT /api/experiments/{id}, DELETE /api/experiments/{id}
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import datetime
import requests
import streamlit as st
from utils import check_auth, format_date, get_auth_headers, resolve_backend_url

check_auth()
BACKEND_URL = resolve_backend_url()

_STATUS_OPTIONS = ["PENDING", "COMPLETED", "OK", "KO", "VALIDATION_ERROR"]

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
                resp = requests.delete(
                    f"{BACKEND_URL}/api/experiments/{exp_id}",
                    headers=get_auth_headers(),
                    timeout=10,
                )
                if resp.status_code == 204:
                    st.session_state.pop("selected_exp_id", None)
                    st.session_state.pop("exp_edit_mode", None)
                    st.switch_page("pages/search_experiments.py")
                else:
                    detail = resp.json().get("message", resp.text)
                    st.error(f"Delete failed ({resp.status_code}): {detail}")
            except requests.exceptions.RequestException as e:
                st.error(f"Request failed: {e}")
    with col_close:
        if st.button("Close", use_container_width=True):
            st.rerun()


# ---------------------------------------------------------------------------
# Load experiment data
# ---------------------------------------------------------------------------

try:
    resp = requests.get(
        f"{BACKEND_URL}/api/experiments/{exp_id}",
        headers=get_auth_headers(),
        timeout=10,
    )
    if resp.status_code != 200:
        st.error(f"Failed to load (HTTP {resp.status_code})")
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
        st.session_state.pop("selected_exp_id", None)
        st.session_state.pop("exp_edit_mode", None)
        st.switch_page("pages/search_experiments.py")

edit_mode = st.session_state.get("exp_edit_mode", False)

with edit_col:
    if not edit_mode:
        if st.button("✏️ Edit", use_container_width=True):
            st.session_state["exp_edit_mode"] = True
            st.rerun()
    else:
        if st.button("Cancel", use_container_width=True):
            st.session_state["exp_edit_mode"] = False
            st.rerun()

with del_col:
    st.markdown('<div class="delete-btn"></div>', unsafe_allow_html=True)
    if st.button("🗑️ Delete", use_container_width=True, help="Delete this experiment"):
        _confirm_delete(data.get("name", str(exp_id)))

# ---------------------------------------------------------------------------
# Export row: PDF | Excel
# ---------------------------------------------------------------------------

pdf_col, xlsx_col, _ = st.columns([1, 1, 5])

with pdf_col:
    if st.button("📄 Export PDF", use_container_width=True, help="Download Certificate of Analysis"):
        with st.spinner("Generating PDF…"):
            try:
                r = requests.get(
                    f"{BACKEND_URL}/api/export/experiments/{exp_id}/pdf",
                    headers=get_auth_headers(),
                    timeout=30,
                )
                if r.status_code == 200:
                    st.session_state[f"export_pdf_{exp_id}"] = r.content
                else:
                    st.error(f"PDF export failed ({r.status_code})")
            except requests.exceptions.RequestException as e:
                st.error(f"Request failed: {e}")

with xlsx_col:
    if st.button("📊 Export Excel", use_container_width=True, help="Download XLSX workbook"):
        with st.spinner("Generating Excel…"):
            try:
                r = requests.get(
                    f"{BACKEND_URL}/api/export/experiments/{exp_id}/xlsx",
                    headers=get_auth_headers(),
                    timeout=30,
                )
                if r.status_code == 200:
                    st.session_state[f"export_xlsx_{exp_id}"] = r.content
                else:
                    st.error(f"Excel export failed ({r.status_code})")
            except requests.exceptions.RequestException as e:
                st.error(f"Request failed: {e}")

# Show download buttons once bytes are ready (persist across reruns via session_state)
_pdf_bytes = st.session_state.get(f"export_pdf_{exp_id}")
_xlsx_bytes = st.session_state.get(f"export_xlsx_{exp_id}")
if _pdf_bytes or _xlsx_bytes:
    dl_pdf_col, dl_xlsx_col, _ = st.columns([1, 1, 5])
    if _pdf_bytes:
        with dl_pdf_col:
            st.download_button(
                "⬇️ Download PDF",
                data=_pdf_bytes,
                file_name=f"CoA_experiment_{exp_id}.pdf",
                mime="application/pdf",
                use_container_width=True,
            )
    if _xlsx_bytes:
        with dl_xlsx_col:
            st.download_button(
                "⬇️ Download Excel",
                data=_xlsx_bytes,
                file_name=f"experiment_{exp_id}.xlsx",
                mime="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                use_container_width=True,
            )

st.title("Experiment Details")
st.markdown("---")

# ---------------------------------------------------------------------------
# Metadata section
# ---------------------------------------------------------------------------

batches = data.get("usedReagentBatches", [])
pairs = data.get("measurementPairs", [])

with st.form("edit_form"):
    st.subheader("Experiment Details")

    col_name, col_status = st.columns([3, 1])
    with col_name:
        edit_name = st.text_input("Name", value=data.get("name", ""), disabled=not edit_mode)
    with col_status:
        current_status = data.get("status", "OK")
        status_idx = _STATUS_OPTIONS.index(current_status) if current_status in _STATUS_OPTIONS else 0
        edit_status = st.selectbox("Status", _STATUS_OPTIONS, index=status_idx, disabled=not edit_mode)

    col_date, col_proto, col_curve = st.columns(3)
    with col_date:
        existing_date = None
        if data.get("date"):
            try:
                existing_date = datetime.date.fromisoformat(data["date"][:10])
            except ValueError:
                pass
        edit_date = st.date_input("Date", value=existing_date, disabled=not edit_mode)

    with col_proto:
        st.text_input("Protocol (read-only)", value=data.get("protocolName", "—"), disabled=True)

    with col_curve:
        _CURVE_DISPLAY = {
            "FOUR_PARAMETER_LOGISTIC": "4PL",
            "FIVE_PARAMETER_LOGISTIC": "5PL",
            "LOG_LOGISTIC_3P": "3PL",
            "LINEAR": "Linear",
            "SEMI_LOG_LINEAR": "Semi-log Linear",
            "POINT_TO_POINT": "Point-to-Point",
        }
        curve_raw = data.get("protocolCurveType", "")
        curve_label = _CURVE_DISPLAY.get(curve_raw, curve_raw or "—")
        st.text_input("Curve Type (read-only)", value=curve_label, disabled=True)

    st.markdown("---")

    # Reagent batches (lot + expiry editable)
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
            disabled=not edit_mode,
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
            disabled=not edit_mode,
        )
        batch_lots.append(lot)
        batch_expiries.append(expiry)

    # ---------------------------------------------------------------------------
    # Measurement Pairs section
    # ---------------------------------------------------------------------------
    st.markdown("---")
    st.subheader(f"Measurement Pairs ({len(pairs)} pair{'s' if len(pairs) != 1 else ''})")

    if not edit_mode:
        # Read-only dataframe view
        if pairs:
            st.dataframe(
                [
                    {
                        "Type": p.get("pairType"),
                        "Conc.": p.get("concentrationNominal"),
                        "Signal 1": p.get("signal1"),
                        "Signal 2": p.get("signal2"),
                        "Mean": p.get("signalMean"),
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
        saved = False
        st.form_submit_button("(view only — click ✏️ Edit to modify)", disabled=True, use_container_width=True)
    else:
        # Editable signal inputs per pair
        pair_s1: list[float] = []
        pair_s2: list[float] = []
        pair_conc: list[float | None] = []

        if pairs:
            header_cols = st.columns([1.5, 1.5, 1.5, 1.5, 1.5, 1])
            for lbl, col in zip(
                ["Type", "Conc.", "Signal 1", "Signal 2", "Mean*", "%CV*"], header_cols
            ):
                col.caption(f"**{lbl}**")

            for i, p in enumerate(pairs):
                cols = st.columns([1.5, 1.5, 1.5, 1.5, 1.5, 1])
                cols[0].caption(p.get("pairType", ""))
                conc = cols[1].number_input(
                    "Conc",
                    value=float(p.get("concentrationNominal") or 0.0),
                    step=0.001,
                    format="%.4f",
                    key=f"pair_conc_{i}",
                    label_visibility="collapsed",
                )
                s1 = cols[2].number_input(
                    "S1",
                    value=float(p.get("signal1") or 0.0),
                    step=0.0001,
                    format="%.4f",
                    key=f"pair_s1_{i}",
                    label_visibility="collapsed",
                )
                s2 = cols[3].number_input(
                    "S2",
                    value=float(p.get("signal2") or 0.0),
                    step=0.0001,
                    format="%.4f",
                    key=f"pair_s2_{i}",
                    label_visibility="collapsed",
                )
                mean_calc = (s1 + s2) / 2.0
                cv_calc = abs(s1 - s2) / mean_calc * 100 if mean_calc != 0 else 0.0
                cols[4].caption(f"{mean_calc:.4f}")
                cols[5].caption(f"{cv_calc:.1f}%")

                pair_s1.append(s1)
                pair_s2.append(s2)
                pair_conc.append(conc if conc != 0.0 else p.get("concentrationNominal"))
        else:
            st.info("No measurement pairs.")

        st.caption("* Mean and %CV are recalculated live from Signal 1 and Signal 2.")

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
            if batch_lots[i].strip()
        ]

        measurement_pair_updates = [
            {
                "id": pairs[i]["id"],
                "signal1": pair_s1[i],
                "signal2": pair_s2[i],
                "concentrationNominal": pair_conc[i],
            }
            for i in range(len(pairs))
        ] if pairs else []

        payload = {
            "name": edit_name.strip(),
            "date": datetime.datetime.combine(edit_date, datetime.time(0, 0)).isoformat(),
            "status": edit_status,
            "reagentBatchUpdates": reagent_batch_updates,
            "measurementPairUpdates": measurement_pair_updates,
        }
        try:
            put_resp = requests.put(
                f"{BACKEND_URL}/api/experiments/{exp_id}",
                json=payload,
                headers=get_auth_headers(),
                timeout=10,
            )
            if put_resp.status_code == 200:
                st.success("Changes saved successfully.")
                st.session_state["exp_edit_mode"] = False
                data = put_resp.json()
                st.rerun()
            else:
                detail = put_resp.json().get("message", put_resp.text)
                st.error(f"Save failed ({put_resp.status_code}): {detail}")
        except requests.exceptions.RequestException as e:
            st.error(f"Request failed: {e}")

# ---------------------------------------------------------------------------
# Audit footer
# ---------------------------------------------------------------------------

st.markdown("---")
audit_cols = st.columns(3)
audit_cols[0].caption(f"Created by: {data.get('createdBy', '—')}")
audit_cols[1].caption(f"Created at: {format_date(data.get('createdAt'))}")
audit_cols[2].caption(f"Last updated: {format_date(data.get('updatedAt'))}")
