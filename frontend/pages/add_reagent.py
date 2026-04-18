"""
Add Reagent page.

Simple form to add a single reagent to the catalog (name, manufacturer, optional description).
API: POST /api/reagent-catalogs
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import requests
import streamlit as st
from utils import check_auth, get_auth_headers, resolve_backend_url, show_stored_errors, translate_error, warn_if_form_dirty

check_auth()
warn_if_form_dirty()
BACKEND_URL = resolve_backend_url()

if st.button("← Torna alla Dashboard"):
    st.switch_page("pages/dashboard.py")

st.title("Nuovo Reagente")
show_stored_errors("add_reagent")

# ── Post-save success state ───────────────────────────────────────────────────
if "reagent_created" in st.session_state:
    created = st.session_state["reagent_created"]
    st.success(f"✅ Reagente **{created['name']}** ({created['manufacturer']}) creato con successo!")
    c1, c2 = st.columns(2)
    with c1:
        if st.button("➕ Crea un altro reagente", type="primary", use_container_width=True):
            for k in ("rgt_name", "rgt_mfr", "rgt_desc"):
                st.session_state.pop(k, None)
            st.session_state.pop("reagent_created", None)
            st.rerun()
    with c2:
        if st.button("← Torna alla Dashboard", use_container_width=True):
            st.session_state.pop("reagent_created", None)
            st.switch_page("pages/dashboard.py")
    st.stop()

st.markdown("---")

# Top-level error placeholder (filled by backend error responses)
top_error_ph = st.empty()

name = st.text_input("Nome *", placeholder="e.g. Anti-Human IgG", key="rgt_name")
name_ph = st.empty()
if not name.strip():
    name_ph.warning("⚠️ Il nome è obbligatorio")

manufacturer = st.text_input("Produttore *", placeholder="e.g. Sigma-Aldrich", key="rgt_mfr")
mfr_ph = st.empty()
if not manufacturer.strip():
    mfr_ph.warning("⚠️ Il produttore è obbligatorio")

description = st.text_area("Descrizione", placeholder="Note facoltative", key="rgt_desc")

has_errors = not name.strip() or not manufacturer.strip()

if st.button(
    "Aggiungi Reagente",
    type="primary",
    use_container_width=True,
    disabled=has_errors,
    key="rgt_submit",
):
    name_ph.empty()
    mfr_ph.empty()
    top_error_ph.empty()

    payload = {
        "name": name.strip(),
        "manufacturer": manufacturer.strip(),
        "description": description.strip(),
    }
    try:
        resp = requests.post(
            f"{BACKEND_URL}/api/reagent-catalogs",
            json=payload,
            headers=get_auth_headers(),
            timeout=10,
        )
        if resp.status_code == 201:
            data = resp.json()
            st.session_state["reagent_created"] = {
                "id": data["id"],
                "name": name.strip(),
                "manufacturer": manufacturer.strip(),
            }
            st.rerun()
        else:
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
            elif "manufacturer" in message.lower():
                mfr_ph.error(f"⚠️ {translated}")
            else:
                top_error_ph.error(f"Errore ({resp.status_code}): {translated}")
    except requests.exceptions.RequestException as e:
        top_error_ph.error(translate_error(f"Errore di rete: {e}"))
