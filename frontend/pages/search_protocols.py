"""
Search Protocols page.

Searches protocols by partial name match (case-insensitive) with pagination.
Each result row has a Details button that navigates to the Protocol Details page.
API: GET /api/protocols/search?name=...&page=...&size=...
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import requests
import streamlit as st
from utils import check_auth, get_auth_headers, resolve_backend_url, show_persistent_error, show_stored_errors, translate_error

check_auth()
BACKEND_URL = resolve_backend_url()

if st.button("← Torna alla Dashboard"):
    st.switch_page("pages/dashboard.py")

st.title("Cerca Protocolli")
show_stored_errors("search_protocols")
st.markdown("---")

with st.form("search_protocol_form"):
    col1, col2 = st.columns([4, 1])
    with col1:
        name_filter = st.text_input("Nome contiene", placeholder="Lascia vuoto per mostrarli tutti")
    with col2:
        page_size = st.selectbox("Risultati per pagina", [10, 20, 50], index=1)
    submitted = st.form_submit_button("Cerca", use_container_width=True)

st.session_state.setdefault("proto_page", 0)

if submitted or "proto_results" in st.session_state:
    if submitted:
        st.session_state["proto_page"] = 0

    params = {
        "page": st.session_state.get("proto_page", 0),
        "size": page_size,
    }
    if name_filter and name_filter.strip():
        params["name"] = name_filter.strip()

    try:
        resp = requests.get(
            f"{BACKEND_URL}/api/protocols/search",
            params=params,
            headers=get_auth_headers(),
            timeout=10,
        )
        if resp.status_code == 200:
            st.session_state["proto_results"] = resp.json()
        else:
            show_persistent_error(translate_error(f"Search failed (HTTP {resp.status_code})"), key="search_protocols")
            st.session_state.pop("proto_results", None)
    except requests.exceptions.RequestException as e:
        show_persistent_error(translate_error(str(e)), key="search_protocols")
        st.session_state.pop("proto_results", None)

results = st.session_state.get("proto_results")
if results is not None:
    content = results.get("content", [])
    total = results.get("totalElements", 0)
    cur_page = results.get("number", 0)
    total_pages = results.get("totalPages", 0)

    if not content:
        st.info("Nessun protocollo trovato.")
    else:
        st.caption(f"{total} totali — pagina {cur_page + 1} di {total_pages or 1}")
        for proto in content:
            with st.container(border=True):
                c1, c2, c3, c4, c5 = st.columns([3, 2, 2, 2, 1])
                c1.markdown(f"**{proto.get('name')}**")
                c2.caption(f"Coppie calibrazione: {proto.get('numCalibrationPairs')}")
                c3.caption(f"Coppie controllo: {proto.get('numControlPairs')}")
                c4.caption(
                    f"Max %CV: {proto.get('maxCvAllowed')}  |  "
                    f"Max %Err: {proto.get('maxErrorAllowed')}"
                )
                proto_id = proto.get("id")
                if c5.button("Dettagli", key=f"proto_detail_{proto_id}", use_container_width=True):
                    st.session_state["selected_protocol_id"] = proto_id
                    st.session_state.pop("protocol_edit_mode", None)
                    st.switch_page("pages/protocol_details.py")

        if total_pages > 1:
            st.markdown("---")
            nav = st.columns([1, 1])
            with nav[0]:
                if cur_page > 0 and st.button("← Precedente", use_container_width=True):
                    st.session_state["proto_page"] = cur_page - 1
                    st.rerun()
            with nav[1]:
                if cur_page < total_pages - 1 and st.button("Successiva →", use_container_width=True):
                    st.session_state["proto_page"] = cur_page + 1
                    st.rerun()
