"""
Search Protocols page.

Searches protocols by partial name match (case-insensitive).
API: GET /api/protocols/search?name=...
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import requests
import streamlit as st
from utils import check_auth, inject_global_css, render_logo, render_sidebar, resolve_backend_url

BACKEND_URL = resolve_backend_url()

check_auth()

st.set_page_config(page_title="Search Protocols", page_icon="🔍", layout="wide")

inject_global_css()

_ASSETS = os.path.join(os.path.dirname(__file__), "..", "..", "assets")
render_logo(_ASSETS)
render_sidebar(BACKEND_URL)

if st.button("← Back to Dashboard"):
    st.switch_page("app.py")

st.title("Search Protocols")
st.markdown("---")

with st.form("search_protocol_form"):
    name_filter = st.text_input("Name contains", placeholder="Leave blank to show all")
    submitted = st.form_submit_button("Search", use_container_width=True)

if submitted or "proto_results" in st.session_state:
    if submitted:
        params = {}
        if name_filter and name_filter.strip():
            params["name"] = name_filter.strip()
        try:
            resp = requests.get(f"{BACKEND_URL}/api/protocols/search", params=params, timeout=10)
            if resp.status_code == 200:
                st.session_state["proto_results"] = resp.json()
            else:
                st.error(f"Search failed (HTTP {resp.status_code})")
                st.session_state.pop("proto_results", None)
        except requests.exceptions.RequestException as e:
            st.error(f"Request failed: {e}")
            st.session_state.pop("proto_results", None)

results = st.session_state.get("proto_results")
if results is not None:
    if not results:
        st.info("No protocols found.")
    else:
        st.caption(f"{len(results)} protocol(s) found")
        for proto in results:
            with st.container(border=True):
                c1, c2, c3, c4 = st.columns([3, 2, 2, 2])
                c1.markdown(f"**{proto.get('name')}**")
                c2.caption(f"Calibration pairs: {proto.get('numCalibrationPairs')}")
                c3.caption(f"Control pairs: {proto.get('numControlPairs')}")
                c4.caption(
                    f"Max %CV: {proto.get('maxCvAllowed')}  |  Max %Error: {proto.get('maxErrorAllowed')}"
                )
