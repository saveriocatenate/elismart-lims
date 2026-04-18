"""
Add Experiment page.

Two entry modes:
  - Manual entry: live %CV feedback on signal inputs (existing flow).
  - Import from CSV: upload a plate-reader export, map wells to pair types, POST to
    /api/experiments/{id}/import-csv.

API: GET /api/protocols, GET /api/protocols/{id}, GET /api/protocol-reagent-specs,
     POST /api/experiments, POST /api/experiments/{id}/import-csv
"""
import io
import json
import math
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import datetime
import pandas as pd
import requests
import streamlit as st
from utils import check_auth, get_auth_headers, resolve_backend_url, show_persistent_error, show_stored_errors, translate_error

check_auth()
BACKEND_URL = resolve_backend_url()

if st.button("← Torna alla Dashboard"):
    st.switch_page("pages/dashboard.py")

st.title("Nuovo Esperimento")
show_stored_errors("add_experiment")

# ── Post-save success state ───────────────────────────────────────────────────
if "exp_created" in st.session_state:
    created = st.session_state["exp_created"]
    msg = f"✅ Esperimento **{created['name']}** creato con successo!"
    if created.get("pairs_count") is not None:
        msg += f" ({created['pairs_count']} coppie importate)"
    st.success(msg)
    c1, c2, c3 = st.columns(3)
    with c1:
        if st.button("🔬 Vai al dettaglio", type="primary", use_container_width=True):
            st.session_state["selected_exp_id"] = created["id"]
            st.session_state.pop("exp_created", None)
            st.switch_page("pages/experiment_details.py")
    with c2:
        if st.button("➕ Crea un altro esperimento", use_container_width=True):
            st.session_state.pop("exp_created", None)
            for k in list(st.session_state.keys()):
                if k.startswith(("cal_", "ctrl_", "wmap_", "editor_")):
                    st.session_state.pop(k, None)
            for k in ("exp_name", "exp_date"):
                st.session_state.pop(k, None)
            st.rerun()
    with c3:
        if st.button("← Dashboard", use_container_width=True):
            st.session_state.pop("exp_created", None)
            st.switch_page("pages/dashboard.py")
    st.stop()

st.markdown("---")


# ---------------------------------------------------------------------------
# Cached API helpers
# ---------------------------------------------------------------------------

@st.cache_data(ttl=300, show_spinner=False)
def _load_protocols(backend: str, token: str) -> list:
    try:
        r = requests.get(f"{backend}/api/protocols",
                         headers={"Authorization": f"Bearer {token}"}, timeout=10)
        return r.json() if r.status_code == 200 else []
    except requests.exceptions.RequestException:
        return []


@st.cache_data(ttl=60, show_spinner=False)
def _load_protocol_detail(backend: str, protocol_id: int, token: str) -> dict | None:
    try:
        r = requests.get(f"{backend}/api/protocols/{protocol_id}",
                         headers={"Authorization": f"Bearer {token}"}, timeout=10)
        return r.json() if r.status_code == 200 else None
    except requests.exceptions.RequestException:
        return None


@st.cache_data(ttl=60, show_spinner=False)
def _load_reagent_specs(backend: str, protocol_id: int, token: str) -> list:
    try:
        r = requests.get(f"{backend}/api/protocol-reagent-specs",
                         params={"protocolId": protocol_id},
                         headers={"Authorization": f"Bearer {token}"}, timeout=10)
        return r.json() if r.status_code == 200 else []
    except requests.exceptions.RequestException:
        return []


@st.cache_data(ttl=30, show_spinner=False)
def _load_batches(backend: str, reagent_id: int, token: str) -> list:
    """Return all registered batches for a given reagent catalog entry."""
    try:
        r = requests.get(f"{backend}/api/reagent-batches",
                         params={"reagentId": reagent_id},
                         headers={"Authorization": f"Bearer {token}"}, timeout=10)
        return r.json() if r.status_code == 200 else []
    except requests.exceptions.RequestException:
        return []


def _batch_label(b: dict) -> str:
    """Format a ReagentBatch dict as a human-readable selectbox label."""
    lot = b.get("lotNumber", "?")
    exp_str = b.get("expiryDate")
    if not exp_str:
        return f"Lotto {lot} — senza scadenza"
    try:
        exp_date = datetime.date.fromisoformat(exp_str)
        today = datetime.date.today()
        days_left = (exp_date - today).days
        exp_fmt = exp_date.strftime("%d/%m/%Y")
        if days_left < 0:
            return f"Lotto {lot} — Scad. {exp_fmt}  ⚠️ (SCADUTO)"
        if days_left <= 30:
            return f"Lotto {lot} — Scad. {exp_fmt}  ⏰ (Scade tra {days_left} giorni)"
        return f"Lotto {lot} — Scad. {exp_fmt}"
    except ValueError:
        return f"Lotto {lot} — Scad. {exp_str}"


# ---------------------------------------------------------------------------
# Live %CV helpers (manual mode)
# ---------------------------------------------------------------------------

def _compute_cv(s1: float, s2: float) -> float | None:
    mean = (s1 + s2) / 2.0
    if mean == 0.0:
        return None
    return (abs(s1 - s2) / math.sqrt(2) / mean) * 100.0


def _cv_badge(s1: float, s2: float, max_cv: float | None) -> str:
    if s1 == 0.0 and s2 == 0.0:
        return '<span style="color:#9E9E9E;font-size:0.85em">—</span>'
    cv = _compute_cv(s1, s2)
    if cv is None:
        return '<span style="color:#9E9E9E;font-size:0.85em">N/A</span>'
    if max_cv is None:
        color = "#607D8B"
    elif cv <= max_cv:
        color = "#2E7D32"
    elif cv <= max_cv * 1.5:
        color = "#F9A825"
    else:
        color = "#C62828"
    return f'<span style="color:{color};font-weight:600;font-size:0.9em">%CV: {cv:.1f}%</span>'


# ---------------------------------------------------------------------------
# Import results table helper
# ---------------------------------------------------------------------------

def _display_imported_pairs(pairs: list, max_cv_threshold: float | None) -> None:
    """Render the imported measurement pairs as a colour-coded dataframe."""
    rows = []
    for p in pairs:
        cv = p.get("cvPct")
        if cv is None:
            cv_display, cv_color = "—", "#9E9E9E"
        elif max_cv_threshold is None:
            cv_display, cv_color = f"{cv:.1f}%", "#607D8B"
        elif cv <= max_cv_threshold:
            cv_display, cv_color = f"{cv:.1f}%", "#2E7D32"
        elif cv <= max_cv_threshold * 1.5:
            cv_display, cv_color = f"{cv:.1f}%", "#F9A825"
        else:
            cv_display, cv_color = f"{cv:.1f}%", "#C62828"

        rows.append({
            "Tipo":      p.get("pairType", "—"),
            "Conc.":     p.get("concentrationNominal"),
            "Segnale 1": p.get("signal1"),
            "Segnale 2": p.get("signal2"),
            "Media":     p.get("signalMean"),
            "%CV":       cv_display,
            "_cv_color": cv_color,
            "Outlier":   "⚑" if p.get("isOutlier") else "",
        })

    df = pd.DataFrame(rows)

    def _style_cv(row):
        color = row["_cv_color"]
        styles = [""] * len(row)
        styles[df.columns.get_loc("%CV")] = f"color: {color}; font-weight: 600"
        return styles

    st.dataframe(
        df.drop(columns=["_cv_color"]).style.apply(_style_cv, axis=1),
        use_container_width=True,
        hide_index=True,
    )


# ---------------------------------------------------------------------------
# Protocol selection
# ---------------------------------------------------------------------------

token = st.session_state.get("jwt_token", "")
protocols = _load_protocols(BACKEND_URL, token)

if not protocols:
    st.warning("Nessun protocollo trovato. Crea prima un protocollo.")
    st.stop()

protocol_map = {p["id"]: p["name"] for p in protocols}
selected_protocol_id = st.selectbox(
    "Protocollo", options=list(protocol_map.keys()),
    format_func=lambda x: protocol_map[x], key="sel_protocol",
)

protocol_detail = _load_protocol_detail(BACKEND_URL, selected_protocol_id, token)
reagent_specs = _load_reagent_specs(BACKEND_URL, selected_protocol_id, token)

if not protocol_detail:
    show_persistent_error("Impossibile caricare i dettagli del protocollo.")
    st.stop()

num_cal = protocol_detail.get("numCalibrationPairs", 0)
num_ctrl = protocol_detail.get("numControlPairs", 0)
max_cv = protocol_detail.get("maxCvAllowed")

st.caption(
    f"Protocollo: **{protocol_detail['name']}** — "
    f"{num_cal} coppie calibrazione, {num_ctrl} coppie controllo, "
    f"max CV {max_cv}%, max errore {protocol_detail.get('maxErrorAllowed')}%"
)

st.markdown("---")

# ---------------------------------------------------------------------------
# Entry mode toggle
# ---------------------------------------------------------------------------

input_mode = st.radio(
    "Modalità inserimento",
    options=["Inserimento manuale", "Importa da CSV"],
    horizontal=True,
    key="input_mode",
)

st.markdown("---")

# ---------------------------------------------------------------------------
# Shared: Experiment metadata
# ---------------------------------------------------------------------------

st.subheader("Dettagli Esperimento")

# Top-level error placeholder (filled by backend error responses)
exp_top_error_ph = st.empty()

col_name, col_status = st.columns([3, 1])
with col_name:
    exp_name = st.text_input("Nome *", placeholder="es. IgG Corsa 2026-04-06", key="exp_name")
with col_status:
    exp_status = st.selectbox(
        "Stato", ["PENDING", "COMPLETED"], key="exp_status"
    )
exp_name_ph = st.empty()
if not exp_name.strip():
    exp_name_ph.warning("⚠️ Il nome è obbligatorio")
exp_date = st.date_input("Data", value=datetime.date.today(), key="exp_date")

st.markdown("---")

# ---------------------------------------------------------------------------
# Shared: Reagent Batches — selectbox per reagent + inline "New Lot" form
# ---------------------------------------------------------------------------

st.subheader(f"Lotti Reagenti ({len(reagent_specs)} reagent{'i' if len(reagent_specs) != 1 else 'e'})")
if not reagent_specs:
    st.info("Questo protocollo non ha reagenti definiti.")

# selected_batch_ids[i] = the ReagentBatch.id chosen for reagent_specs[i], or None
selected_batch_ids: list[int | None] = []

for i, spec in enumerate(reagent_specs):
    reagent_id = spec["reagentId"]
    is_mandatory = spec.get("isMandatory", False)
    mandatory_badge = (
        "&nbsp;<span style='background:#C62828;color:white;border-radius:4px;"
        "padding:2px 7px;font-size:0.75em;font-weight:600'>obbligatorio</span>"
        if is_mandatory else
        "&nbsp;<span style='background:#546E7A;color:white;border-radius:4px;"
        "padding:2px 7px;font-size:0.75em'>opzionale</span>"
    )

    batches = _load_batches(BACKEND_URL, reagent_id, token)

    with st.container(border=True):
        st.markdown(
            f"**🧪 {spec['reagentName']}**{mandatory_badge}",
            unsafe_allow_html=True,
        )

        if batches:
            batch_map = {b["id"]: b for b in batches}
            batch_ids = [b["id"] for b in batches]

            sel_id = st.selectbox(
                "Seleziona lotto",
                options=batch_ids,
                format_func=lambda bid, bm=batch_map: _batch_label(bm[bid]),
                key=f"batch_sel_{i}",
                label_visibility="collapsed",
            )
            selected_batch_ids.append(sel_id)
        else:
            st.warning(f"Nessun lotto registrato per **{spec['reagentName']}**. Creane uno qui sotto.")
            selected_batch_ids.append(None)

        # ── Inline "New Lot" form ────────────────────────────────────────
        with st.expander("➕ Registra nuovo lotto", expanded=False):
            with st.form(f"new_batch_{i}", clear_on_submit=True):
                fc1, fc2, fc3 = st.columns([3, 2, 3])
                nl_lot = fc1.text_input("Numero Lotto *", placeholder="LOT-2026-001",
                                        key=f"nl_lot_{i}")
                nl_expiry = fc2.date_input("Data Scadenza *", value=None,
                                           key=f"nl_expiry_{i}")
                nl_supplier = fc3.text_input("Fornitore (opzionale)", key=f"nl_supplier_{i}")
                nl_notes = st.text_input("Note (opzionale)", key=f"nl_notes_{i}")
                register_btn = st.form_submit_button("Registra", type="primary",
                                                     use_container_width=True)

            if register_btn:
                if not nl_lot.strip():
                    show_persistent_error("Il numero di lotto è obbligatorio.")
                elif nl_expiry is None:
                    show_persistent_error("La data di scadenza è obbligatoria.")
                else:
                    payload = {
                        "reagentId": reagent_id,
                        "lotNumber": nl_lot.strip(),
                        "expiryDate": nl_expiry.isoformat(),
                        "supplier": nl_supplier.strip() or None,
                        "notes": nl_notes.strip() or None,
                    }
                    try:
                        pr = requests.post(
                            f"{BACKEND_URL}/api/reagent-batches",
                            json=payload,
                            headers=get_auth_headers(),
                            timeout=10,
                        )
                        if pr.status_code == 201:
                            st.success(f"Lotto **{nl_lot.strip()}** registrato.")
                            _load_batches.clear()
                            st.rerun()
                        else:
                            detail = pr.json().get("message", pr.text)
                            show_persistent_error(translate_error(detail), key="add_experiment")
                    except requests.exceptions.RequestException as e:
                        show_persistent_error(translate_error(str(e)), key="add_experiment")

st.markdown("---")

# ---------------------------------------------------------------------------
# Shared helper: build used_batches payload
# ---------------------------------------------------------------------------

def _build_used_batches() -> list | None:
    """Returns the usedReagentBatches list, or None if mandatory reagents are missing."""
    missing = [
        reagent_specs[i]["reagentName"]
        for i in range(len(reagent_specs))
        if reagent_specs[i].get("isMandatory") and selected_batch_ids[i] is None
    ]
    if missing:
        show_persistent_error(
            f"È necessario selezionare (o creare) un lotto per i reagenti obbligatori: "
            f"{', '.join(missing)}"
        )
        return None
    return [
        {"reagentBatchId": selected_batch_ids[i]}
        for i in range(len(reagent_specs))
        if selected_batch_ids[i] is not None
    ]


# ---------------------------------------------------------------------------
# Mode A: Manual entry
# ---------------------------------------------------------------------------

if input_mode == "Inserimento manuale":

    def _pair_header():
        c1, c2, c3, c4 = st.columns([2.5, 2.5, 2, 2])
        c1.caption("**Segnale 1**")
        c2.caption("**Segnale 2**")
        c3.caption("**%CV live**")
        c4.caption("**Conc. Nominale**")

    def _pair_row(prefix: str, idx: int) -> tuple:
        c1, c2, c3, c4 = st.columns([2.5, 2.5, 2, 2])
        s1 = c1.number_input("Segnale 1", key=f"{prefix}_s1_{idx}", value=0.0,
                             step=0.001, format="%.4f", label_visibility="collapsed")
        s2 = c2.number_input("Segnale 2", key=f"{prefix}_s2_{idx}", value=0.0,
                             step=0.001, format="%.4f", label_visibility="collapsed")
        c3.markdown(_cv_badge(s1, s2, max_cv), unsafe_allow_html=True)
        conc = c4.number_input("Conc. Nominale", key=f"{prefix}_conc_{idx}", value=0.0,
                               step=0.001, format="%.4f", label_visibility="collapsed")
        return s1, s2, conc

    st.subheader(f"Coppie di Calibrazione ({num_cal})")
    _pair_header()
    cal_s1, cal_s2, cal_conc = [], [], []
    for i in range(num_cal):
        s1, s2, c = _pair_row("cal", i)
        cal_s1.append(s1); cal_s2.append(s2); cal_conc.append(c)
    st.caption("Inserisci la concentrazione nominale nella stessa unità di misura usata per i calibratori.")

    st.markdown("---")

    st.subheader(f"Coppie di Controllo ({num_ctrl})")
    _pair_header()
    ctrl_s1, ctrl_s2, ctrl_conc = [], [], []
    for i in range(num_ctrl):
        s1, s2, c = _pair_row("ctrl", i)
        ctrl_s1.append(s1); ctrl_s2.append(s2); ctrl_conc.append(c)
    st.caption("Inserisci la concentrazione nominale nella stessa unità di misura usata per i calibratori.")

    st.markdown("---")

    if st.button(
        "Crea Esperimento",
        type="primary",
        use_container_width=True,
        disabled=not exp_name.strip(),
        key="btn_manual",
    ):
        exp_name_ph.empty()
        exp_top_error_ph.empty()

        used_batches = _build_used_batches()
        if used_batches is None:
            st.stop()

        def _pairs(pt, s1l, s2l, cl):
            return [{"pairType": pt, "concentrationNominal": c if c != 0.0 else None,
                     "signal1": s1, "signal2": s2, "isOutlier": False}
                    for s1, s2, c in zip(s1l, s2l, cl)]

        payload = {
            "name": exp_name.strip(),
            "date": datetime.datetime.combine(exp_date, datetime.time(0, 0)).isoformat(),
            "protocolId": selected_protocol_id,
            "status": exp_status,
            "usedReagentBatches": used_batches,
            "measurementPairs": _pairs("CALIBRATION", cal_s1, cal_s2, cal_conc)
                              + _pairs("CONTROL", ctrl_s1, ctrl_s2, ctrl_conc),
        }

        try:
            resp = requests.post(f"{BACKEND_URL}/api/experiments", json=payload,
                                 headers=get_auth_headers(), timeout=15)
            if resp.status_code == 201:
                exp_id = resp.json().get("id")
                st.session_state["selected_exp_id"] = exp_id
                st.session_state["exp_created"] = {"id": exp_id, "name": exp_name.strip()}
                st.rerun()
            else:
                try:
                    body = resp.json()
                    message = body.get("message", resp.text)
                except Exception:
                    message = resp.text
                translated = translate_error(message)
                if resp.status_code in (409, 500):
                    exp_top_error_ph.error(f"Errore ({resp.status_code}): {translated}")
                elif "name" in message.lower():
                    exp_name_ph.error(f"⚠️ {translated}")
                else:
                    exp_top_error_ph.error(f"Errore ({resp.status_code}): {translated}")
        except requests.exceptions.RequestException as e:
            exp_top_error_ph.error(translate_error(f"Errore di rete: {e}"))


# ---------------------------------------------------------------------------
# Mode B: Import from CSV
# ---------------------------------------------------------------------------

else:
    # ── 1. File upload ────────────────────────────────────────────────────
    csv_file = st.file_uploader(
        "Carica file CSV",
        type=["csv"],
        key="csv_upload",
        help="Esporta dal tuo lettore di piastre. Accettato: .csv",
    )

    if csv_file is None:
        st.info("Carica un file CSV per continuare.")
        st.stop()

    # ── 2. Format selector ───────────────────────────────────────────────
    csv_format = "GENERIC"
    st.caption(
        "ℹ️ Formato supportato: **Generic** (colonne configurabili). "
        "Tecan Magellan, BioTek Gen5 e Molecular Devices SoftMax Pro: _prossimamente_."
    )

    # ── 3. CSV preview (first 5 rows) ────────────────────────────────────
    csv_file.seek(0)
    try:
        df_preview = pd.read_csv(io.BytesIO(csv_file.read()), nrows=5)
    except Exception as e:
        show_persistent_error(f"Impossibile analizzare il file CSV: {e}")
        st.stop()

    st.caption(f"Anteprima — {csv_file.name} ({csv_file.size:,} byte)")
    st.dataframe(df_preview, use_container_width=True, hide_index=True)

    columns = list(df_preview.columns)
    if not columns:
        show_persistent_error("Il file CSV non contiene colonne.")
        st.stop()

    # ── 4. Column mapping ────────────────────────────────────────────────
    st.subheader("Mappatura Colonne")
    st.caption("Seleziona quali colonne CSV corrispondono all'identificatore del pozzetto e ai valori di segnale.")

    c1, c2, c3 = st.columns(3)
    well_col    = c1.selectbox("Colonna pozzetto",    columns, key="well_col")
    signal1_col = c2.selectbox("Colonna Segnale 1", columns,
                               index=min(1, len(columns) - 1), key="sig1_col")
    signal2_col = c3.selectbox("Colonna Segnale 2", columns,
                               index=min(2, len(columns) - 1), key="sig2_col")

    if len({well_col, signal1_col, signal2_col}) < 3:
        st.warning("Pozzetto, Segnale 1 e Segnale 2 devono essere tre colonne diverse.")
        st.stop()

    # ── 5. Read all wells from the full CSV ──────────────────────────────
    csv_file.seek(0)
    try:
        df_full = pd.read_csv(io.BytesIO(csv_file.read()))
    except Exception as e:
        show_persistent_error(f"Impossibile rileggere il CSV: {e}")
        st.stop()

    if well_col not in df_full.columns:
        show_persistent_error(f"La colonna '{well_col}' non è presente nel file.")
        st.stop()

    unique_wells = sorted(df_full[well_col].dropna().astype(str).unique().tolist())
    if not unique_wells:
        show_persistent_error("Nessun identificatore di pozzetto trovato nella colonna selezionata.")
        st.stop()

    # ── 6. Plate layout data_editor ──────────────────────────────────────
    st.markdown("---")
    st.subheader("Mappatura Layout Piastra")
    st.caption(
        f"{len(unique_wells)} pozzetti univoci trovati nella colonna **{well_col}**. "
        "Assegna un Tipo Coppia a ogni pozzetto da importare. "
        "Lascia *(salta)* per escludere un pozzetto."
    )

    # Stable key: reset the editor table when the file or well column changes
    mapping_state_key = f"wmap_{csv_file.name}_{well_col}"
    if mapping_state_key not in st.session_state:
        st.session_state[mapping_state_key] = pd.DataFrame({
            "Pozzetto":       unique_wells,
            "Tipo Coppia":    ["(salta)"] * len(unique_wells),
            "Conc. Nominale": [None] * len(unique_wells),
        })

    edited_mapping: pd.DataFrame = st.data_editor(
        st.session_state[mapping_state_key],
        column_config={
            "Pozzetto": st.column_config.TextColumn("Pozzetto", disabled=True),
            "Tipo Coppia": st.column_config.SelectboxColumn(
                "Tipo Coppia",
                options=["(salta)", "CALIBRATION", "CONTROL", "SAMPLE"],
                required=True,
            ),
            "Conc. Nominale": st.column_config.NumberColumn(
                "Conc. Nominale",
                help="Concentrazione nominale (qualsiasi unità). Lascia vuoto per campioni incogniti.",
                min_value=0.0,
                format="%.4f",
            ),
        },
        hide_index=True,
        use_container_width=True,
        key=f"editor_{mapping_state_key}",
        num_rows="fixed",
    )

    # Persist edits back to session state so they survive rerenders
    st.session_state[mapping_state_key] = edited_mapping

    # Summary badge
    mapped_wells = edited_mapping[edited_mapping["Tipo Coppia"] != "(salta)"]
    if len(mapped_wells) == 0:
        st.warning("Nessun pozzetto mappato. Assegna almeno un pozzetto per procedere.")

    # ── 7. Create & Import button ────────────────────────────────────────
    st.markdown("---")

    col_btn, col_info = st.columns([2, 3])
    with col_info:
        if len(mapped_wells) > 0:
            type_counts = mapped_wells["Tipo Coppia"].value_counts().to_dict()
            badge_parts = [f"{v} {k}" for k, v in type_counts.items()]
            st.caption(f"Pronto per l'importazione: {', '.join(badge_parts)}")

    with col_btn:
        do_import = st.button(
            "Crea e Importa",
            type="primary",
            use_container_width=True,
            key="btn_csv_import",
            disabled=(len(mapped_wells) == 0 or not exp_name.strip()),
        )

    if do_import:
        exp_name_ph.empty()
        exp_top_error_ph.empty()

        used_batches = _build_used_batches()
        if used_batches is None:
            st.stop()

        # ── Step 1: Create experiment skeleton (no pairs) ────────────────
        exp_payload = {
            "name": exp_name.strip(),
            "date": datetime.datetime.combine(exp_date, datetime.time(0, 0)).isoformat(),
            "protocolId": selected_protocol_id,
            "status": exp_status,
            "usedReagentBatches": used_batches,
            "measurementPairs": [],
        }

        with st.spinner("Creazione esperimento in corso…"):
            try:
                create_resp = requests.post(
                    f"{BACKEND_URL}/api/experiments",
                    json=exp_payload,
                    headers=get_auth_headers(),
                    timeout=15,
                )
            except requests.exceptions.RequestException as e:
                show_persistent_error(translate_error(f"Errore di rete durante la creazione dell'esperimento: {e}"), key="add_experiment")
                st.stop()

        if create_resp.status_code != 201:
            try:
                body = create_resp.json()
                detail = body.get("message", create_resp.text)
            except Exception:
                detail = create_resp.text
            translated = translate_error(detail)
            if create_resp.status_code in (409, 500):
                exp_top_error_ph.error(f"Errore ({create_resp.status_code}): {translated}")
            elif "name" in detail.lower():
                exp_name_ph.error(f"⚠️ {translated}")
            else:
                exp_top_error_ph.error(f"Errore ({create_resp.status_code}): {translated}")
            st.stop()

        exp_id = create_resp.json().get("id")
        st.session_state["selected_exp_id"] = exp_id

        # ── Step 2: Build import config ──────────────────────────────────
        well_mapping: dict = {}
        for _, row in edited_mapping.iterrows():
            ptype = row["Tipo Coppia"]
            if ptype == "(salta)":
                continue
            conc_raw = row["Conc. Nominale"]
            conc = float(conc_raw) if pd.notna(conc_raw) and conc_raw is not None else None
            well_mapping[str(row["Pozzetto"])] = {
                "pairType": ptype,
                "concentrationNominal": conc,
            }

        import_config = {
            "format":        csv_format,
            "wellColumn":    well_col,
            "signal1Column": signal1_col,
            "signal2Column": signal2_col,
            "wellMapping":   well_mapping,
        }

        # ── Step 3: POST import-csv ───────────────────────────────────────
        csv_file.seek(0)
        csv_bytes = csv_file.read()

        with st.spinner("Importazione coppie di misura…"):
            try:
                import_resp = requests.post(
                    f"{BACKEND_URL}/api/experiments/{exp_id}/import-csv",
                    files=[
                        ("file",   (csv_file.name, csv_bytes, "text/csv")),
                        ("config", ("config.json",
                                    json.dumps(import_config).encode("utf-8"),
                                    "application/json")),
                    ],
                    headers=get_auth_headers(),
                    timeout=30,
                )
            except requests.exceptions.RequestException as e:
                st.warning(
                    f"L'esperimento **{exp_id}** è stato creato ma l'importazione CSV è fallita "
                    f"per un errore di rete: {e}"
                )
                st.stop()

        if import_resp.status_code != 200:
            detail = import_resp.json().get("message", import_resp.text)
            st.warning(
                f"L'esperimento **{exp_id}** è stato creato ma l'importazione è fallita "
                f"({import_resp.status_code}): {detail}"
            )
            st.stop()

        # ── Step 4: Display results ──────────────────────────────────────
        imported_pairs = import_resp.json().get("measurementPairs", [])

        if imported_pairs:
            _display_imported_pairs(imported_pairs, max_cv)

        st.session_state["exp_created"] = {
            "id": exp_id,
            "name": exp_name.strip(),
            "pairs_count": len(imported_pairs),
        }
        st.rerun()

