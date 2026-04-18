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
from utils import check_auth, get_auth_headers, resolve_backend_url, show_persistent_error, show_stored_errors, translate_error

check_auth()
BACKEND_URL = resolve_backend_url()

_CURVE_TYPE_OPTIONS: dict[str, str] = {
    "4PL — Four Parameter Logistic (ELISA standard)": "FOUR_PARAMETER_LOGISTIC",
    "5PL — Five Parameter Logistic (asymmetric)": "FIVE_PARAMETER_LOGISTIC",
    "3PL — Log-Logistic (minimum fixed at zero)": "LOG_LOGISTIC_3P",
    "Linear (y = mx + q)": "LINEAR",
    "Semi-log Linear (log X-axis)": "SEMI_LOG_LINEAR",
    "Point-to-Point (not recommended)": "POINT_TO_POINT",
}
_CURVE_LABEL_BY_VALUE: dict[str, str] = {v: k for k, v in _CURVE_TYPE_OPTIONS.items()}

protocol_id = st.session_state.get("selected_protocol_id")
if not protocol_id:
    st.warning("Nessun protocollo selezionato.")
    st.stop()


@st.dialog("Conferma salvataggio")
def _confirm_save_proto(proto_name: str) -> None:
    """Modal dialog that asks the user to confirm saving protocol changes.

    Reads the staged payload from ``st.session_state["proto_pending_save"]``,
    executes the PUT request on confirmation, and clears the pending state.
    """
    payload = st.session_state.get("proto_pending_save", {})
    st.write(f"Stai per salvare le modifiche al protocollo **{proto_name}**. Confermi?")
    col_save, col_cancel = st.columns(2)
    with col_save:
        if st.button("Conferma", type="primary", use_container_width=True):
            try:
                put_resp = requests.put(
                    f"{BACKEND_URL}/api/protocols/{protocol_id}",
                    json=payload,
                    headers=get_auth_headers(),
                    timeout=10,
                )
                if put_resp.status_code == 200:
                    st.session_state.pop("proto_pending_save", None)
                    st.session_state["protocol_edit_mode"] = False
                    st.session_state["proto_save_success"] = True
                    st.rerun()
                else:
                    detail = put_resp.json().get("message", put_resp.text)
                    show_persistent_error(translate_error(detail), key="protocol_details")
            except requests.exceptions.RequestException as e:
                show_persistent_error(translate_error(str(e)), key="protocol_details")
    with col_cancel:
        if st.button("Annulla", use_container_width=True):
            st.session_state.pop("proto_pending_save", None)
            st.rerun()


@st.dialog("Conferma Eliminazione")
def _confirm_delete(proto_name: str) -> None:
    """Modal dialog to confirm protocol deletion."""
    st.write(f"Eliminare il protocollo **{proto_name}**?")
    st.caption(
        "⚠️ Questa operazione è irreversibile. "
        "L'eliminazione è bloccata se ci sono esperimenti collegati a questo protocollo."
    )
    col_del, col_close = st.columns(2)
    with col_del:
        if st.button("Elimina", type="primary", use_container_width=True):
            try:
                resp = requests.delete(
                    f"{BACKEND_URL}/api/protocols/{protocol_id}",
                    headers=get_auth_headers(),
                    timeout=10,
                )
                if resp.status_code == 204:
                    st.session_state.pop("selected_protocol_id", None)
                    st.session_state.pop("protocol_edit_mode", None)
                    st.switch_page("pages/search_protocols.py")
                else:
                    detail = resp.json().get("message", resp.text)
                    show_persistent_error(translate_error(detail), key="protocol_details")
            except requests.exceptions.RequestException as e:
                show_persistent_error(translate_error(str(e)), key="protocol_details")
    with col_close:
        if st.button("Chiudi", use_container_width=True):
            st.rerun()


# ---------------------------------------------------------------------------
# Load protocol data
# ---------------------------------------------------------------------------

try:
    resp = requests.get(
        f"{BACKEND_URL}/api/protocols/{protocol_id}",
        headers=get_auth_headers(),
        timeout=10,
    )
    if resp.status_code != 200:
        show_persistent_error(translate_error(f"Failed to load protocol (HTTP {resp.status_code})"))
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
    if st.button("← Torna alla Ricerca"):
        st.session_state.pop("selected_protocol_id", None)
        st.session_state.pop("protocol_edit_mode", None)
        st.switch_page("pages/search_protocols.py")

edit_mode = st.session_state.get("protocol_edit_mode", False)

with edit_col:
    if not edit_mode:
        if st.button("✏️ Modifica", use_container_width=True):
            st.session_state["protocol_edit_mode"] = True
            st.rerun()
    else:
        if st.button("Annulla", use_container_width=True):
            st.session_state["protocol_edit_mode"] = False
            st.session_state.pop("proto_pending_save", None)
            st.rerun()

with del_col:
    if st.button("🗑️ Elimina", use_container_width=True):
        _confirm_delete(data.get("name", str(protocol_id)))

st.title("Dettagli Protocollo")
show_stored_errors("protocol_details")

if st.session_state.pop("proto_save_success", False):
    st.success("Protocollo aggiornato con successo.")

st.info(
    "Modifica ed Eliminazione sono bloccate dal server se ci sono esperimenti collegati a questo protocollo. "
    "Rimuovi prima tutti gli esperimenti collegati."
)

if edit_mode:
    st.markdown(
        "<div style='border:2px solid #2E7D32;border-radius:6px;padding:0.5rem 1rem;"
        "margin-bottom:1rem;background:#F1F8E9'>"
        "<b style='color:#2E7D32'>✏️ Modalità modifica attiva</b> — "
        "modifica i campi e clicca <b>Salva Modifiche</b> per confermare, "
        "oppure <b>Annulla</b> per annullare.</div>",
        unsafe_allow_html=True,
    )
else:
    st.markdown(
        "<div style='border:1px solid #BDBDBD;border-radius:6px;padding:0.5rem 1rem;"
        "margin-bottom:1rem;background:#FAFAFA'>"
        "<span style='color:#757575'>👁️ Modalità visualizzazione</span> — "
        "clicca <b>✏️ Modifica</b> per modificare i dati.</div>",
        unsafe_allow_html=True,
    )

st.markdown("---")

# ---------------------------------------------------------------------------
# Detail form (read-only or editable)
# ---------------------------------------------------------------------------

with st.form("protocol_form"):
    col1, col2 = st.columns(2)
    with col1:
        edit_name = st.text_input(
            "Nome", value=data.get("name", ""), disabled=not edit_mode
        )
        edit_cv = st.number_input(
            "Max %CV Consentito",
            value=float(data.get("maxCvAllowed", 0.0)),
            min_value=0.0,
            step=0.5,
            format="%.2f",
            disabled=not edit_mode,
        )
        edit_cal = st.number_input(
            "Coppie di Calibrazione",
            value=int(data.get("numCalibrationPairs", 1)),
            min_value=1,
            step=1,
            disabled=not edit_mode,
        )
    with col2:
        current_curve_value = data.get("curveType", "FOUR_PARAMETER_LOGISTIC")
        curve_labels = list(_CURVE_TYPE_OPTIONS.keys())
        current_curve_label = _CURVE_LABEL_BY_VALUE.get(current_curve_value, curve_labels[0])
        edit_curve_label = st.selectbox(
            "Tipo di Curva",
            options=curve_labels,
            index=curve_labels.index(current_curve_label),
            disabled=not edit_mode,
        )
        edit_error = st.number_input(
            "Max %Errore Consentito",
            value=float(data.get("maxErrorAllowed", 0.0)),
            min_value=0.0,
            step=0.5,
            format="%.2f",
            disabled=not edit_mode,
        )
        edit_ctrl = st.number_input(
            "Coppie di Controllo",
            value=int(data.get("numControlPairs", 1)),
            min_value=1,
            step=1,
            disabled=not edit_mode,
        )

    if edit_mode:
        saved = st.form_submit_button("Salva Modifiche", type="primary", use_container_width=True)
    else:
        saved = False
        st.form_submit_button("(visualizzazione — clicca ✏️ Modifica per editare)", disabled=True, use_container_width=True)

# ---------------------------------------------------------------------------
# Stage payload and open confirmation dialog
# ---------------------------------------------------------------------------

if saved:
    if not edit_name.strip():
        show_persistent_error("Il nome è obbligatorio.", key="protocol_details")
    else:
        st.session_state["proto_pending_save"] = {
            "name": edit_name.strip(),
            "curveType": _CURVE_TYPE_OPTIONS[edit_curve_label],
            "numCalibrationPairs": int(edit_cal),
            "numControlPairs": int(edit_ctrl),
            "maxCvAllowed": float(edit_cv),
            "maxErrorAllowed": float(edit_error),
            "concentrationUnit": data.get("concentrationUnit", "ng/mL"),
        }

if st.session_state.get("proto_pending_save"):
    pending_name = st.session_state["proto_pending_save"].get("name", data.get("name", ""))
    _confirm_save_proto(pending_name)
