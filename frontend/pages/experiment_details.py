"""
Experiment Details page.

Read/write view of a single experiment.
- Default view: all fields shown read-only (click ✏️ Edit to enable editing).
- EDIT button (green outlined) enables editing of metadata, reagent batches, and
  measurement pair signal values. The total number of pairs cannot change.
- A grey banner indicates VIEW mode; a green banner indicates EDIT mode.
- Saving changes requires explicit confirmation via a modal dialog.
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
from utils import check_auth, color_code_qc, format_date, get_auth_headers, resolve_backend_url, show_persistent_error, show_stored_errors, translate_error

check_auth()
BACKEND_URL = resolve_backend_url()

_STATUS_OPTIONS = ["PENDING", "COMPLETED", "OK", "KO", "VALIDATION_ERROR"]

_CURVE_DISPLAY = {
    "FOUR_PARAMETER_LOGISTIC": "4PL",
    "FIVE_PARAMETER_LOGISTIC": "5PL",
    "LOG_LOGISTIC_3P": "3PL",
    "LINEAR": "Linear",
    "SEMI_LOG_LINEAR": "Semi-log Linear",
    "POINT_TO_POINT": "Point-to-Point",
}

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
                    show_persistent_error(translate_error(detail), key="experiment_details")
            except requests.exceptions.RequestException as e:
                show_persistent_error(translate_error(str(e)), key="experiment_details")
    with col_close:
        if st.button("Close", use_container_width=True):
            st.rerun()


@st.dialog("Conferma salvataggio")
def _confirm_save_exp(exp_name: str) -> None:
    """Modal dialog that asks the user to confirm saving experiment changes.

    Reads the staged payload from ``st.session_state["exp_pending_save"]``,
    executes the PUT request on confirmation, and clears the pending state.
    """
    payload = st.session_state.get("exp_pending_save", {})
    st.write(f"Stai per salvare le modifiche all'esperimento **{exp_name}**. Confermi?")
    col_save, col_cancel = st.columns(2)
    with col_save:
        if st.button("Conferma", type="primary", use_container_width=True):
            try:
                put_resp = requests.put(
                    f"{BACKEND_URL}/api/experiments/{exp_id}",
                    json=payload,
                    headers=get_auth_headers(),
                    timeout=10,
                )
                if put_resp.status_code == 200:
                    st.session_state.pop("exp_pending_save", None)
                    st.session_state["exp_edit_mode"] = False
                    st.session_state["exp_save_success"] = True
                    st.rerun()
                else:
                    detail = put_resp.json().get("message", put_resp.text)
                    show_persistent_error(translate_error(detail), key="experiment_details")
            except requests.exceptions.RequestException as e:
                show_persistent_error(translate_error(str(e)), key="experiment_details")
    with col_cancel:
        if st.button("Annulla", use_container_width=True):
            st.session_state.pop("exp_pending_save", None)
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
        show_persistent_error(translate_error(f"Failed to load (HTTP {resp.status_code})"))
        st.stop()
    data = resp.json()
except requests.exceptions.RequestException as e:
    show_persistent_error(translate_error(str(e)))
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
            st.session_state.pop("exp_pending_save", None)
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
                    show_persistent_error(translate_error(f"PDF export failed ({r.status_code})"), key="experiment_details")
            except requests.exceptions.RequestException as e:
                show_persistent_error(translate_error(str(e)), key="experiment_details")

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
                    show_persistent_error(translate_error(f"Excel export failed ({r.status_code})"), key="experiment_details")
            except requests.exceptions.RequestException as e:
                show_persistent_error(translate_error(str(e)), key="experiment_details")

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
show_stored_errors("experiment_details")

# Show persistent success message after a save, with anchor link back to pairs section
if st.session_state.pop("exp_save_success", False):
    st.success("Modifiche salvate con successo.")
    st.markdown("[↓ Torna ai risultati](#measurement-pairs)")

st.markdown("---")

# ---------------------------------------------------------------------------
# Mode banner
# ---------------------------------------------------------------------------

batches = data.get("usedReagentBatches", [])
pairs = data.get("measurementPairs", [])

if edit_mode:
    st.markdown(
        "<div style='border:2px solid #2E7D32;border-radius:6px;padding:0.5rem 1rem;"
        "margin-bottom:1rem;background:#F1F8E9'>"
        "<b style='color:#2E7D32'>✏️ Modalità modifica attiva</b> — "
        "modifica i campi e clicca <b>Salva modifiche</b> per confermare, "
        "oppure <b>Cancel</b> per annullare.</div>",
        unsafe_allow_html=True,
    )
else:
    st.markdown(
        "<div style='border:1px solid #BDBDBD;border-radius:6px;padding:0.5rem 1rem;"
        "margin-bottom:1rem;background:#FAFAFA'>"
        "<span style='color:#757575'>👁️ Modalità visualizzazione</span> — "
        "clicca <b>✏️ Edit</b> per modificare i dati.</div>",
        unsafe_allow_html=True,
    )

# ---------------------------------------------------------------------------
# Metadata section
# ---------------------------------------------------------------------------

exp_date_str = data.get("date", "")[:10]  # "YYYY-MM-DD" for expiry comparison

if not edit_mode:
    # ── Read-only metadata display ──────────────────────────────────────────
    st.subheader("Experiment Details")
    col_name, col_status = st.columns([3, 1])
    col_name.metric("Name", data.get("name", "—"))
    col_status.metric("Status", data.get("status", "—"))

    col_date, col_proto, col_curve = st.columns(3)
    col_date.metric("Date", format_date(data.get("date")))
    col_proto.metric("Protocol", data.get("protocolName", "—"))
    curve_raw = data.get("protocolCurveType", "")
    col_curve.metric("Curve Type", _CURVE_DISPLAY.get(curve_raw, curve_raw or "—"))

    st.markdown("---")

    # ── Read-only reagent batches ────────────────────────────────────────────
    st.subheader(f"Reagent Batches ({len(batches)} reagent{'s' if len(batches) != 1 else ''})")
    for b in batches:
        rb = b.get("reagentBatch") or {}
        reagent_name = rb.get("reagentName", "—")
        lot_number = rb.get("lotNumber", "—")
        expiry_str = rb.get("expiryDate")
        supplier = rb.get("supplier") or "—"

        c1, c2, c3, c4 = st.columns([3, 2, 2, 2])
        c1.markdown(f"**{reagent_name}**")
        c2.caption(f"Lotto: {lot_number}")
        c3.caption(f"Scad.: {format_date(expiry_str)}")
        c4.caption(f"Fornitore: {supplier}")

        if expiry_str and exp_date_str:
            try:
                if expiry_str < exp_date_str:
                    st.warning(
                        f"⚠️ Il lotto **{lot_number}** era scaduto alla data "
                        f"dell'esperimento ({format_date(exp_date_str)})."
                    )
            except Exception:
                pass

    # ── Read-only measurement pairs ──────────────────────────────────────────
    st.markdown("---")
    st.subheader(f"Measurement Pairs ({len(pairs)} pair{'s' if len(pairs) != 1 else ''})", anchor="measurement-pairs")

    if pairs:
        max_cv = data.get("protocolMaxCvAllowed")
        max_err = data.get("protocolMaxErrorAllowed")

        header_cells = "".join(
            f"<th style='padding:6px 10px;text-align:left;border-bottom:2px solid #ccc'>{h}</th>"
            for h in ["Type", "Conc.", "Signal 1", "Signal 2", "Mean", "%CV", "%Recovery", "Status", "Outlier"]
        )
        rows_html = ""
        for p in pairs:
            is_outlier = p.get("isOutlier") or False
            row_bg = "background-color:#FFFDE7" if is_outlier else ""

            cv_val = p.get("cvPct")
            rec_val = p.get("recoveryPct")
            pair_status = p.get("pairStatus")

            cv_style = color_code_qc(cv_val, max_cv, "cv")
            rec_style = color_code_qc(rec_val, max_err, "recovery")

            def _fmt(v: float | None, decimals: int = 4) -> str:
                return f"{v:.{decimals}f}" if v is not None else "—"

            status_icon = "✅" if pair_status == "PASS" else ("❌" if pair_status == "FAIL" else "—")
            outlier_icon = "⚠️" if is_outlier else ""

            cell_style = f"padding:6px 10px;border-bottom:1px solid #eee;{row_bg}"
            rows_html += (
                f"<tr>"
                f"<td style='{cell_style}'>{p.get('pairType','—')}</td>"
                f"<td style='{cell_style}'>{_fmt(p.get('concentrationNominal'))}</td>"
                f"<td style='{cell_style}'>{_fmt(p.get('signal1'))}</td>"
                f"<td style='{cell_style}'>{_fmt(p.get('signal2'))}</td>"
                f"<td style='{cell_style}'>{_fmt(p.get('signalMean'))}</td>"
                f"<td style='{cell_style};{cv_style}'>{_fmt(cv_val, 1)}{'%' if cv_val is not None else ''}</td>"
                f"<td style='{cell_style};{rec_style}'>{_fmt(rec_val, 1)}{'%' if rec_val is not None else ''}</td>"
                f"<td style='{cell_style};text-align:center'>{status_icon}</td>"
                f"<td style='{cell_style};text-align:center'>{outlier_icon}</td>"
                f"</tr>"
            )

        table_html = (
            f"<table style='width:100%;border-collapse:collapse;font-size:0.9rem'>"
            f"<thead><tr style='background:#f5f5f5'>{header_cells}</tr></thead>"
            f"<tbody>{rows_html}</tbody>"
            f"</table>"
        )

        if max_cv is not None or max_err is not None:
            legend_parts = []
            if max_cv is not None:
                legend_parts.append(f"max %CV: <b>{max_cv}%</b>")
            if max_err is not None:
                legend_parts.append(f"max %Error: <b>{max_err}%</b>")
            st.caption("Protocol limits — " + " | ".join(legend_parts))

        st.markdown(table_html, unsafe_allow_html=True)
    else:
        st.info("No measurement pairs.")

else:
    # ── Edit mode form ───────────────────────────────────────────────────────
    with st.form("edit_form"):
        st.subheader("Experiment Details")

        col_name, col_status = st.columns([3, 1])
        with col_name:
            edit_name = st.text_input("Name", value=data.get("name", ""))
        with col_status:
            current_status = data.get("status", "OK")
            status_idx = _STATUS_OPTIONS.index(current_status) if current_status in _STATUS_OPTIONS else 0
            edit_status = st.selectbox("Status", _STATUS_OPTIONS, index=status_idx)

        col_date, col_proto, col_curve = st.columns(3)
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

        with col_curve:
            curve_raw = data.get("protocolCurveType", "")
            curve_label = _CURVE_DISPLAY.get(curve_raw, curve_raw or "—")
            st.text_input("Curve Type (read-only)", value=curve_label, disabled=True)

        st.markdown("---")

        # Reagent batches — selectable in edit mode
        st.subheader(f"Reagent Batches ({len(batches)} reagent{'s' if len(batches) != 1 else ''})")

        selected_batch_ids: list[int | None] = []

        for i, b in enumerate(batches):
            rb = b.get("reagentBatch") or {}
            reagent_name = rb.get("reagentName", "—")
            reagent_id = rb.get("reagentId")
            lot_number = rb.get("lotNumber", "—")
            expiry_str = rb.get("expiryDate")

            if expiry_str and exp_date_str:
                try:
                    if expiry_str < exp_date_str:
                        st.warning(
                            f"⚠️ Il lotto **{lot_number}** era scaduto alla data "
                            f"dell'esperimento ({format_date(exp_date_str)})."
                        )
                except Exception:
                    pass

            available: list = []
            if reagent_id:
                try:
                    r = requests.get(
                        f"{BACKEND_URL}/api/reagent-batches",
                        params={"reagentId": reagent_id},
                        headers=get_auth_headers(),
                        timeout=10,
                    )
                    available = r.json() if r.status_code == 200 else []
                except requests.exceptions.RequestException:
                    available = []

            st.markdown(f"**{reagent_name}**")
            if available:
                batch_map = {ab["id"]: ab for ab in available}
                batch_ids = [ab["id"] for ab in available]
                current_bid = rb.get("id")
                default_idx = batch_ids.index(current_bid) if current_bid in batch_ids else 0

                def _blabel(bid: int, bm: dict = batch_map) -> str:
                    ab = bm[bid]
                    lot = ab.get("lotNumber", "?")
                    exp = ab.get("expiryDate", "")
                    if exp:
                        try:
                            ed = datetime.date.fromisoformat(exp)
                            return f"Lotto {lot} — Scad. {ed.strftime('%d/%m/%Y')}"
                        except ValueError:
                            pass
                    return f"Lotto {lot}"

                sel = st.selectbox(
                    "Batch",
                    options=batch_ids,
                    index=default_idx,
                    format_func=_blabel,
                    key=f"edit_batch_sel_{i}",
                    label_visibility="collapsed",
                )
                selected_batch_ids.append(sel)
            else:
                st.caption(f"Lotto attuale: {lot_number} — nessun altro lotto disponibile")
                selected_batch_ids.append(rb.get("id"))

        # Measurement pairs — editable signals
        st.markdown("---")
        st.subheader(f"Measurement Pairs ({len(pairs)} pair{'s' if len(pairs) != 1 else ''})")

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

        saved = st.form_submit_button("Salva modifiche", type="primary", use_container_width=True)

    # ── Stage payload and open confirmation dialog ───────────────────────────
    if saved:
        if not edit_name.strip():
            show_persistent_error("Name is required.", key="experiment_details")
        else:
            reagent_batch_updates = [
                {
                    "id": batches[i]["id"],
                    "reagentBatchId": selected_batch_ids[i],
                }
                for i in range(len(batches))
                if selected_batch_ids[i] is not None
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

            st.session_state["exp_pending_save"] = {
                "name": edit_name.strip(),
                "date": datetime.datetime.combine(edit_date, datetime.time(0, 0)).isoformat(),
                "status": edit_status,
                "reagentBatchUpdates": reagent_batch_updates,
                "measurementPairUpdates": measurement_pair_updates,
            }

    # Open the confirmation dialog whenever a payload is staged
    if st.session_state.get("exp_pending_save"):
        pending_name = st.session_state["exp_pending_save"].get("name", data.get("name", ""))
        _confirm_save_exp(pending_name)

# ---------------------------------------------------------------------------
# AI Analysis section
# ---------------------------------------------------------------------------

st.markdown("---")
st.subheader("AI Analysis")

# Load existing insights for this experiment (persisted across sessions)
try:
    insights_resp = requests.get(
        f"{BACKEND_URL}/api/ai/insights",
        params={"experimentId": exp_id},
        headers=get_auth_headers(),
        timeout=10,
    )
    existing_insights = insights_resp.json() if insights_resp.status_code == 200 else []
except requests.exceptions.RequestException:
    existing_insights = []

if existing_insights:
    with st.expander(f"Previous analyses ({len(existing_insights)})", expanded=False):
        for ins in existing_insights:
            gen_at = format_date(ins.get("generatedAt"))
            gen_by = ins.get("generatedBy", "—")
            st.markdown(f"**{gen_at}** — _{gen_by}_")
            st.caption(f"**Q:** {ins.get('userQuestion', '')}")
            st.info(ins.get("aiResponse", ""))
            st.markdown("---")

# New analysis input — outside a form so the spinner doesn't block the whole page
ai_question = st.text_area(
    "Ask the AI Analyst",
    placeholder=(
        "e.g. Why did the control fail? "
        "Is the %CV within acceptable limits for all calibrators?"
    ),
    height=100,
    key="ai_question_input",
)

if st.button("Analyze with AI", type="primary", key="ai_analyze_btn"):
    question = (ai_question or "").strip()
    if not question:
        st.warning("Please enter a question before running the analysis.")
    else:
        with st.spinner("AI is analyzing this experiment…"):
            try:
                ai_resp = requests.post(
                    f"{BACKEND_URL}/api/ai/analyze",
                    json={"experimentIds": [exp_id], "userQuestion": question},
                    headers=get_auth_headers(),
                    timeout=120,
                )
                if ai_resp.status_code == 200:
                    st.session_state[f"ai_result_{exp_id}"] = ai_resp.json().get("analysis", "")
                    st.rerun()
                else:
                    detail = ai_resp.json().get("message", ai_resp.text[:300]) if ai_resp.content else ai_resp.text[:300]
                    show_persistent_error(translate_error(detail), key="experiment_details")
            except requests.exceptions.RequestException as e:
                show_persistent_error(translate_error(str(e)), key="experiment_details")

if st.session_state.get(f"ai_result_{exp_id}"):
    st.markdown("**Latest Analysis Result:**")
    st.success(st.session_state[f"ai_result_{exp_id}"])

# ---------------------------------------------------------------------------
# Audit footer
# ---------------------------------------------------------------------------

st.markdown("---")
audit_cols = st.columns(3)
audit_cols[0].caption(f"Created by: {data.get('createdBy', '—')}")
audit_cols[1].caption(f"Created at: {format_date(data.get('createdAt'))}")
audit_cols[2].caption(f"Last updated: {format_date(data.get('updatedAt'))}")
