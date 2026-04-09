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
from utils import check_auth, format_date, resolve_backend_url

check_auth()
BACKEND_URL = resolve_backend_url()

if st.button("← Back to Dashboard"):
    st.switch_page("pages/dashboard.py")

st.title("Search Experiments")
st.markdown("---")

with st.form("search_form"):
    col1, col2, col3 = st.columns(3)
    with col1:
        name_filter = st.text_input("Name contains")
    with col2:
        status_filter = st.selectbox("Status", ["ALL", "OK", "KO", "VALIDATION_ERROR"])
    with col3:
        page_size = st.selectbox("Page size", [10, 20, 50], index=1)

    col_date1, col_date2, col_date3 = st.columns(3)
    with col_date1:
        date_filter = st.date_input("Date", value=None)
    with col_date2:
        date_from_filter = st.date_input("Date from", value=None)
    with col_date3:
        date_to_filter = st.date_input("Date to", value=None)

    submitted = st.form_submit_button("Search", use_container_width=True)

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
    }
    try:
        resp = requests.post(f"{BACKEND_URL}/api/experiments/search", json=payload, timeout=10)
        if resp.status_code == 200:
            st.session_state["exp_results"] = resp.json()
        else:
            st.error(f"Search failed (HTTP {resp.status_code})")
            st.session_state.pop("exp_results", None)
    except requests.exceptions.RequestException as e:
        st.error(f"Request failed: {e}")
        st.session_state.pop("exp_results", None)

results = st.session_state.get("exp_results")
if results:
    content = results.get("content", [])
    total = results.get("totalElements", 0)
    cur_page = results.get("page", 0)
    total_pages = results.get("totalPages", 0)

    if not content:
        st.info("No experiments found.")
    else:
        st.caption(f"{total} total — page {cur_page + 1} of {total_pages or 1}")

        # Derive the set of currently-checked IDs from checkbox widget state keys.
        # We cap at 4; checkboxes beyond the cap are disabled when unchecked.
        checked_ids: list[int] = [
            exp["id"] for exp in content
            if st.session_state.get(f"chk_{exp['id']}", False)
        ]
        at_max = len(checked_ids) >= 4

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
                if c5.button("Details", key=f"detail_{exp_id}", use_container_width=True):
                    st.session_state["selected_exp_id"] = exp_id
                    st.switch_page("pages/experiment_details.py")

        if len(checked_ids) >= 2:
            st.markdown("---")
            st.caption(f"{len(checked_ids)} experiment(s) selected (max 4)")
            if st.button("⚖️ Compare Selected", use_container_width=True, type="primary"):
                st.session_state["compare_exp_ids"] = checked_ids
                # Clear checkboxes
                for exp in content:
                    st.session_state.pop(f"chk_{exp['id']}", None)
                st.switch_page("pages/compare_experiments.py")

        if total_pages > 1:
            st.markdown("---")
            nav = st.columns([1, 1])
            with nav[0]:
                if cur_page > 0 and st.button("← Previous", use_container_width=True):
                    st.session_state["exp_page"] = cur_page - 1
                    st.rerun()
            with nav[1]:
                if cur_page < total_pages - 1 and st.button("Next →", use_container_width=True):
                    st.session_state["exp_page"] = cur_page + 1
                    st.rerun()
