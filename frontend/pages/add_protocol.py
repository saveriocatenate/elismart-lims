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
from utils import check_auth, get_auth_headers, resolve_backend_url, show_stored_errors, translate_error

check_auth()
BACKEND_URL = resolve_backend_url()

if st.button("← Torna alla Dashboard"):
    st.switch_page("pages/dashboard.py")

st.title("Nuovo Protocollo")
show_stored_errors("add_protocol")

# ── Post-save success state ───────────────────────────────────────────────────
if "proto_created" in st.session_state:
    created = st.session_state["proto_created"]
    st.success(f"✅ Protocollo **{created['name']}** creato con successo!")
    c1, c2, c3 = st.columns(3)
    with c1:
        if st.button("🔍 Vai al dettaglio", type="primary", use_container_width=True):
            st.session_state["selected_protocol_id"] = created["id"]
            st.session_state.pop("proto_created", None)
            st.switch_page("pages/protocol_details.py")
    with c2:
        if st.button("➕ Crea un altro protocollo", use_container_width=True):
            for k in list(st.session_state.keys()):
                if k.startswith("proto_") or k in ("reagent_rows", "reagent_row_counter"):
                    st.session_state.pop(k, None)
            st.rerun()
    with c3:
        if st.button("← Torna alla Dashboard", use_container_width=True):
            st.session_state.pop("proto_created", None)
            st.switch_page("pages/dashboard.py")
    st.stop()

st.markdown("---")

# Top-level error placeholder (filled by backend error responses)
top_error_ph = st.empty()

# --- Load existing reagents from catalog (full data for duplicate check) ---
reagent_list: list[dict] = []
reagent_options: dict[int, str] = {}
try:
    resp = requests.get(
        f"{BACKEND_URL}/api/reagent-catalogs",
        params={"size": 1000},
        headers=get_auth_headers(),
        timeout=10,
    )
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

st.subheader("Dettagli Protocollo")
name = st.text_input("Nome *", placeholder="e.g. IgG Test Protocol", key="proto_name")
name_ph = st.empty()
if not name.strip():
    name_ph.warning("⚠️ Il nome è obbligatorio")

# Curve type options: display label → enum value sent to the API
_CURVE_TYPE_OPTIONS: dict[str, str] = {
    "4PL — Four Parameter Logistic (ELISA standard)": "FOUR_PARAMETER_LOGISTIC",
    "5PL — Five Parameter Logistic (asymmetric)": "FIVE_PARAMETER_LOGISTIC",
    "3PL — Log-Logistic (minimum fixed at zero)": "LOG_LOGISTIC_3P",
    "Linear (y = mx + q)": "LINEAR",
    "Semi-log Linear (log X-axis)": "SEMI_LOG_LINEAR",
    "Point-to-Point (not recommended)": "POINT_TO_POINT",
}
_CURVE_TYPE_DESCRIPTIONS: dict[str, str] = {
    "FOUR_PARAMETER_LOGISTIC": (
        "Curva sigmoide simmetrica — standard per ELISA e immunoassay. "
        "Parametri stimati: A (segnale minimo), B (pendenza), C (EC₅₀), D (segnale massimo)."
    ),
    "FIVE_PARAMETER_LOGISTIC": (
        "Curva sigmoide asimmetrica — per curve con pendenza diversa ai due estremi della sigmoide. "
        "Aggiunge il parametro di asimmetria E alla 4PL."
    ),
    "LOG_LOGISTIC_3P": (
        "Variante della 4PL con minimo fissato a zero — riduce il numero di parametri da stimare. "
        "Adatta quando il segnale di fondo è trascurabile."
    ),
    "LINEAR": (
        "Regressione lineare y = mx + q — ideale per range di risposta ristretti e lineari. "
        "Richiede almeno 2 calibratori."
    ),
    "SEMI_LOG_LINEAR": (
        "Regressione lineare sull'asse X logaritmico — estende il range lineare per dati con "
        "distribuzione log-normale (es. RIA, EIA a basse concentrazioni)."
    ),
    "POINT_TO_POINT": (
        "Interpolazione non parametrica tra punti adiacenti — non raccomandata per uso clinico. "
        "Non estrapolazione oltre l'ultimo calibratore."
    ),
}
_UNIT_OPTIONS: list[str] = ["ng/mL", "µg/mL", "pg/mL", "IU/mL", "nmol/L", "pmol/L", "Altro"]
curve_label = st.selectbox(
    "Tipo di Curva",
    options=list(_CURVE_TYPE_OPTIONS.keys()),
    index=0,
    help="Modello matematico usato per il fit della curva di calibrazione.",
    key="proto_curve",
)
curve_type = _CURVE_TYPE_OPTIONS[curve_label]
st.caption(f"ℹ️ {_CURVE_TYPE_DESCRIPTIONS.get(curve_type, '')}")

col1, col2, col3 = st.columns(3)
with col1:
    num_cal = st.number_input("Coppie di Calibrazione", min_value=1, value=5, step=1, key="proto_cal")
with col2:
    num_ctrl = st.number_input("Coppie di Controllo", min_value=1, value=3, step=1, key="proto_ctrl")
with col3:
    max_cv = st.number_input("%CV Massimo", min_value=0.0, step=0.5, value=10.0, format="%.1f", key="proto_cv")
max_error = st.number_input("%Errore Massimo Consentito", min_value=0.0, step=0.5, value=15.0, format="%.1f", key="proto_err")

unit_choice = st.selectbox(
    "Unità di misura delle concentrazioni",
    options=_UNIT_OPTIONS,
    index=0,
    key="proto_unit",
    help="Unità usata per i calibratori e i controlli di questo protocollo.",
)
if unit_choice == "Altro":
    concentration_unit = st.text_input(
        "Specifica unità",
        max_chars=20,
        key="proto_unit_custom",
        placeholder="es. copies/mL",
    ).strip()
else:
    concentration_unit = unit_choice

st.markdown("---")
st.subheader("Reagenti")

# Section 1: select existing reagents
if reagent_options:
    st.markdown("**Seleziona reagenti esistenti dal catalogo**")
    selected_labels = st.multiselect(
        "Reagenti esistenti",
        options=list(reagent_options.values()),
        help="Seleziona i reagenti già presenti nel catalogo",
        label_visibility="collapsed",
        key="proto_existing_reagents",
    )
else:
    st.info("Nessun reagente nel catalogo. Usa il modulo sottostante per aggiungerne di nuovi.")
    selected_labels = []

label_to_id = {v: k for k, v in reagent_options.items()}
selected_reagent_ids = [label_to_id[lbl] for lbl in selected_labels]

# Section 2: add new reagents one row at a time
st.markdown("**Aggiungi nuovi reagenti al catalogo**")
st.caption("Ogni nuovo reagente verrà creato nel catalogo e collegato a questo protocollo.")

if "reagent_rows" not in st.session_state:
    st.session_state["reagent_rows"] = []
if "reagent_row_counter" not in st.session_state:
    st.session_state["reagent_row_counter"] = 0

if st.button("➕ Aggiungi riga reagente", key="add_reagent_row"):
    st.session_state["reagent_row_counter"] += 1
    st.session_state["reagent_rows"].append(st.session_state["reagent_row_counter"])
    st.rerun()

rows: list[int] = st.session_state["reagent_rows"]

rows_partial = False
for row_id in rows:
    c1, c2, c3, c_del = st.columns([2, 2, 3, 0.5])
    with c1:
        st.text_input(f"Nome #{row_id}", key=f"new_r_name_{row_id}", placeholder="e.g. Anti-Human IgA")
    with c2:
        st.text_input(f"Produttore #{row_id}", key=f"new_r_mfr_{row_id}", placeholder="e.g. Sigma-Aldrich")
    with c3:
        st.text_input(f"Descrizione #{row_id} (opzionale)", key=f"new_r_desc_{row_id}")
    with c_del:
        st.markdown("&nbsp;", unsafe_allow_html=True)  # vertical alignment spacer
        st.markdown('<div class="delete-btn"></div>', unsafe_allow_html=True)
        if st.button("✕", key=f"del_row_{row_id}", help="Rimuovi questa riga"):
            st.session_state["reagent_rows"].remove(row_id)
            st.rerun()

    row_nm = st.session_state.get(f"new_r_name_{row_id}", "").strip()
    row_mfr = st.session_state.get(f"new_r_mfr_{row_id}", "").strip()
    if row_nm and not row_mfr:
        st.warning(f"⚠️ Riga #{row_id}: Il produttore è obbligatorio")
        rows_partial = True
    elif row_mfr and not row_nm:
        st.warning(f"⚠️ Riga #{row_id}: Il nome è obbligatorio")
        rows_partial = True

st.markdown("---")

# ---------------------------------------------------------------------------
# Submit
# ---------------------------------------------------------------------------

has_errors = not name.strip() or rows_partial

if st.button(
    "Crea Protocollo",
    type="primary",
    use_container_width=True,
    disabled=has_errors,
    key="proto_submit",
):
    name_ph.empty()
    top_error_ph.empty()

    # Client-side duplicate check for new reagents
    duplicate_found = False
    for row_id in rows:
        nm = st.session_state.get(f"new_r_name_{row_id}", "").strip()
        mfr = st.session_state.get(f"new_r_mfr_{row_id}", "").strip()
        if not nm or not mfr:
            continue
        if (nm.lower(), mfr.lower()) in existing_pairs:
            st.warning(
                f"⚠️ Un reagente di nome **{nm}** del produttore **{mfr}** esiste già nel catalogo. "
                "Rimuovi questa riga oppure usalo dalla sezione 'Seleziona reagenti esistenti'."
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
                    "concentrationUnit": concentration_unit,
                },
                headers=get_auth_headers(),
                timeout=10,
            )
            if resp.status_code != 201:
                try:
                    body = resp.json()
                    message = body.get("message", resp.text)
                except Exception:
                    message = resp.text
                translated = translate_error(message)
                if resp.status_code == 409:
                    top_error_ph.error(f"Conflitto ({resp.status_code}): {translated}")
                elif "name" in message.lower():
                    name_ph.error(f"⚠️ {translated}")
                else:
                    top_error_ph.error(f"Errore ({resp.status_code}): {translated}")
                st.stop()

            protocol_id = resp.json()["id"]

            # 2. Create any new reagents
            new_reagent_ids: list[int] = []
            reagent_errors: list[str] = []
            for row_id in rows:
                nm = st.session_state.get(f"new_r_name_{row_id}", "").strip()
                mfr = st.session_state.get(f"new_r_mfr_{row_id}", "").strip()
                if not nm or not mfr:
                    continue
                r_resp = requests.post(
                    f"{BACKEND_URL}/api/reagent-catalogs",
                    json={
                        "name": nm,
                        "manufacturer": mfr,
                        "description": st.session_state.get(f"new_r_desc_{row_id}", "").strip(),
                    },
                    headers=get_auth_headers(),
                    timeout=10,
                )
                if r_resp.status_code == 201:
                    new_reagent_ids.append(r_resp.json()["id"])
                else:
                    rd = translate_error(r_resp.json().get("message", r_resp.text))
                    reagent_errors.append(f"'{nm}' ({r_resp.status_code}): {rd}")

            if reagent_errors:
                top_error_ph.error(
                    "Errori nella creazione di alcuni reagenti:\n"
                    + "\n".join(reagent_errors)
                )
                st.stop()

            # 3. Link all selected + new reagents to the protocol
            link_errors: list[str] = []
            for rid in selected_reagent_ids + new_reagent_ids:
                s_resp = requests.post(
                    f"{BACKEND_URL}/api/protocol-reagent-specs",
                    json={"protocolId": protocol_id, "reagentId": rid, "isMandatory": True},
                    headers=get_auth_headers(),
                    timeout=10,
                )
                if s_resp.status_code != 201:
                    sd = s_resp.json().get("message", s_resp.text)
                    link_errors.append(f"Reagent {rid} ({s_resp.status_code}): {sd}")

            if link_errors:
                st.warning(
                    "Protocollo creato, ma alcuni collegamenti ai reagenti sono falliti:\n"
                    + "\n".join(link_errors)
                )

            # Reset new-reagent rows and surface post-save success state
            st.session_state["reagent_rows"] = []
            st.session_state["reagent_row_counter"] = 0
            st.session_state["proto_created"] = {"id": protocol_id, "name": name.strip()}
            st.rerun()

        except requests.exceptions.RequestException as e:
            top_error_ph.error(translate_error(f"Errore di rete: {e}"))
