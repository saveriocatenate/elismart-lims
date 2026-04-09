"""
Search Reagents page.

Searches reagent catalog entries by partial name and/or manufacturer match (case-insensitive).
API: GET /api/reagent-catalogs/search?name=...&manufacturer=...
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import requests
import streamlit as st
from utils import check_auth, resolve_backend_url

check_auth()
BACKEND_URL = resolve_backend_url()

if st.button("← Back to Dashboard"):
    st.switch_page("pages/dashboard.py")

st.title("Search Reagents")
st.markdown("---")

with st.form("search_reagent_form"):
    col1, col2, col3 = st.columns([3, 3, 1])
    with col1:
        name_filter = st.text_input("Name contains", placeholder="Leave blank to skip")
    with col2:
        mfr_filter = st.text_input("Manufacturer contains", placeholder="Leave blank to skip")
    with col3:
        page_size = st.selectbox("Page size", [10, 20, 50], index=1)
    submitted = st.form_submit_button("Search", use_container_width=True)

st.session_state.setdefault("reagent_page", 0)

if submitted or "reagent_results" in st.session_state:
    if submitted:
        st.session_state["reagent_page"] = 0

    params = {
        "page": st.session_state.get("reagent_page", 0),
        "size": page_size,
    }
    if name_filter and name_filter.strip():
        params["name"] = name_filter.strip()
    if mfr_filter and mfr_filter.strip():
        params["manufacturer"] = mfr_filter.strip()

    try:
        resp = requests.get(f"{BACKEND_URL}/api/reagent-catalogs/search", params=params, timeout=10)
        if resp.status_code == 200:
            st.session_state["reagent_results"] = resp.json()
        else:
            st.error(f"Search failed (HTTP {resp.status_code})")
            st.session_state.pop("reagent_results", None)
    except requests.exceptions.RequestException as e:
        st.error(f"Request failed: {e}")
        st.session_state.pop("reagent_results", None)

results = st.session_state.get("reagent_results")
if results is not None:
    content = results.get("content", [])
    total = results.get("totalElements", 0)
    cur_page = results.get("number", 0)
    total_pages = results.get("totalPages", 0)

    if not content:
        st.info("No reagents found.")
    else:
        st.caption(f"{total} total — page {cur_page + 1} of {total_pages or 1}")
        for reagent in content:
            with st.container(border=True):
                c1, c2, c3 = st.columns([3, 3, 4])
                c1.markdown(f"**{reagent.get('name')}**")
                c2.caption(reagent.get("manufacturer") or "—")
                c3.caption(reagent.get("description") or "")

        if total_pages > 1:
            st.markdown("---")
            nav = st.columns([1, 1])
            with nav[0]:
                if cur_page > 0 and st.button("← Previous", use_container_width=True):
                    st.session_state["reagent_page"] = cur_page - 1
                    st.rerun()
            with nav[1]:
                if cur_page < total_pages - 1 and st.button("Next →", use_container_width=True):
                    st.session_state["reagent_page"] = cur_page + 1
                    st.rerun()
