"""
Search Experiments page.

Provides filter-based search with pagination. Each result row has a Details button
and a checkbox for multi-experiment comparison (up to 4 at a time).
Selecting 2+ experiments enables the Compare Selected button, which navigates to
the comparison page with the selected IDs pre-loaded.
API: POST /api/experiments/search
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import requests
import streamlit as st
from utils import check_auth, format_date, get_auth_headers, resolve_backend_url, show_persistent_error, show_stored_errors, translate_error, warn_if_form_dirty

check_auth()
warn_if_form_dirty()
BACKEND_URL = resolve_backend_url()

if st.button("← Torna alla Dashboard"):
    st.switch_page("pages/dashboard.py")

st.title("Cerca Esperimenti")
show_stored_errors("search_experiments")
st.markdown("---")

# "Mine" toggle — default ON for analysts/reviewers, OFF for admins who need a global view.
_role = st.session_state.get("role", "ANALYST")
st.session_state.setdefault("mine_exp", _role != "ADMIN")
mine_toggle = st.toggle(
    "Mostra solo i miei esperimenti",
    key="mine_exp",
    help="Attivo: vedi solo gli esperimenti creati da te. Disattiva per vedere tutto il database.",
)

with st.form("search_form"):
    col1, col2, col3 = st.columns(3)
    with col1:
        name_filter = st.text_input("Nome contiene")
    with col2:
        status_filter = st.selectbox(
            "Stato",
            ["ALL", "PENDING", "COMPLETED", "OK", "KO", "VALIDATION_ERROR"],
        )
    with col3:
        page_size = st.selectbox("Risultati per pagina", [10, 20, 50], index=1)

    col_date1, col_date2, col_date3 = st.columns(3)
    with col_date1:
        date_filter = st.date_input("Data", value=None)
    with col_date2:
        date_from_filter = st.date_input("Data da", value=None)
    with col_date3:
        date_to_filter = st.date_input("Data a", value=None)

    submitted = st.form_submit_button("Cerca", use_container_width=True)

st.session_state.setdefault("exp_page", 0)

if submitted or "exp_results" in st.session_state:
    payload = {
        "name": name_filter if name_filter else None,
        "date": date_filter.isoformat() + "T00:00:00" if date_filter else None,
        "dateFrom": date_from_filter.isoformat() + "T00:00:00" if date_from_filter else None,
        "dateTo": date_to_filter.isoformat() + "T23:59:59" if date_to_filter else None,
        "status": status_filter if status_filter != "ALL" else None,
        "page": st.session_state.get("exp_page", 0),
        "size": page_size,
        "mine": mine_toggle,
    }
    try:
        resp = requests.post(
            f"{BACKEND_URL}/api/experiments/search",
            json=payload,
            headers=get_auth_headers(),
            timeout=10,
        )
        if resp.status_code == 200:
            st.session_state["exp_results"] = resp.json()
        else:
            show_persistent_error(translate_error(f"Search failed (HTTP {resp.status_code})"), key="search_experiments")
            st.session_state.pop("exp_results", None)
    except requests.exceptions.RequestException as e:
        show_persistent_error(translate_error(str(e)), key="search_experiments")
        st.session_state.pop("exp_results", None)

results = st.session_state.get("exp_results")
if results:
    content = results.get("content", [])
    total = results.get("totalElements", 0)
    cur_page = results.get("page", 0)
    total_pages = results.get("totalPages", 0)

    if not content:
        st.info("Nessun esperimento trovato.")
    else:
        st.caption(f"{total} totali — pagina {cur_page + 1} di {total_pages or 1}")

        # Derive the set of currently-checked IDs from checkbox widget state keys.
        # We cap at 4; checkboxes beyond the cap are disabled when unchecked.
        checked_ids: list[int] = [
            exp["id"] for exp in content
            if st.session_state.get(f"chk_{exp['id']}", False)
        ]
        at_max = len(checked_ids) >= 4

        # Selection counter — always visible when at least one is checked
        if checked_ids:
            counter_col, _ = st.columns([2, 5])
            with counter_col:
                if len(checked_ids) > 4:
                    st.warning(f"📊 {len(checked_ids)} esperimenti selezionati — massimo 4")
                else:
                    st.info(f"📊 {len(checked_ids)} esperiment{'o' if len(checked_ids) == 1 else 'i'} selezionat{'o' if len(checked_ids) == 1 else 'i'}")

        for exp in content:
            with st.container(border=True):
                c0, c1, c2, c3, c4, c5 = st.columns([0.5, 3, 2, 1, 1, 1])
                exp_id = exp["id"]
                is_checked = st.session_state.get(f"chk_{exp_id}", False)
                c0.checkbox(
                    "",
                    value=is_checked,
                    key=f"chk_{exp_id}",
                    disabled=at_max and not is_checked,
                )
                c1.markdown(f"**{exp.get('name')}**")
                c2.caption(format_date(exp.get("date")))
                c3.markdown(f"🏷️ {exp.get('protocolName', '—')}")
                status = exp.get("status", "")
                emoji = "✅" if status == "OK" else "🔴" if status == "KO" else ""
                c4.caption(f"{emoji} {status}")
                if c5.button("Dettagli", key=f"detail_{exp_id}", use_container_width=True):
                    st.session_state["selected_exp_id"] = exp_id
                    st.switch_page("pages/experiment_details.py")

        if len(checked_ids) >= 2:
            st.markdown("---")
            act_compare, act_export = st.columns(2)

            with act_compare:
                too_many = len(checked_ids) > 4
                if st.button(
                    "⚖️ Confronta Selezionati",
                    use_container_width=True,
                    type="primary",
                    disabled=too_many,
                    help="Massimo 4 esperimenti" if too_many else None,
                ):
                    st.session_state["compare_exp_ids"] = checked_ids
                    # Clear checkboxes
                    for exp in content:
                        st.session_state.pop(f"chk_{exp['id']}", None)
                    st.switch_page("pages/compare_experiments.py")

            with act_export:
                if st.button("📊 Esporta Selezionati in Excel", use_container_width=True):
                    with st.spinner("Generazione Excel in corso…"):
                        try:
                            r = requests.post(
                                f"{BACKEND_URL}/api/export/experiments/xlsx",
                                json=checked_ids,
                                headers=get_auth_headers(),
                                timeout=60,
                            )
                            if r.status_code == 200:
                                st.session_state["batch_xlsx_bytes"] = r.content
                                st.session_state["batch_xlsx_ids"] = list(checked_ids)
                            else:
                                show_persistent_error(translate_error(f"Batch Excel export failed ({r.status_code})"), key="search_experiments")
                        except requests.exceptions.RequestException as e:
                            show_persistent_error(translate_error(str(e)), key="search_experiments")

            # Show download button once bytes are available for the current selection
            _batch_bytes = st.session_state.get("batch_xlsx_bytes")
            _batch_ids = st.session_state.get("batch_xlsx_ids", [])
            if _batch_bytes and set(_batch_ids) == set(checked_ids):
                st.download_button(
                    "⬇️ Scarica Excel Batch",
                    data=_batch_bytes,
                    file_name="experiments_batch.xlsx",
                    mime="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    use_container_width=True,
                )

        if total_pages > 1:
            st.markdown("---")
            nav = st.columns([1, 1])
            with nav[0]:
                if cur_page > 0 and st.button("← Precedente", use_container_width=True):
                    st.session_state["exp_page"] = cur_page - 1
                    st.rerun()
            with nav[1]:
                if cur_page < total_pages - 1 and st.button("Successiva →", use_container_width=True):
                    st.session_state["exp_page"] = cur_page + 1
                    st.rerun()
