"""
Dashboard page.

Checks backend health on load and provides navigation buttons to all main pages.
API: GET /api/health
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import requests
import streamlit as st
from utils import resolve_backend_url

BACKEND_URL = resolve_backend_url()


def _check_backend():
    """Return (healthy: bool, detail: str | dict)."""
    try:
        resp = requests.get(f"{BACKEND_URL}/api/health", timeout=5)
        if resp.status_code == 200:
            return True, resp.json()
        return False, f"Backend returned status {resp.status_code}"
    except requests.exceptions.ConnectionError:
        return False, "Cannot reach backend. Is the Spring Boot application running?"
    except requests.exceptions.Timeout:
        return False, "Backend request timed out."


st.title("Dashboard")

healthy, detail = _check_backend()
if healthy:
    st.success(f"Backend is online — {detail.get('timestamp', '')}")
else:
    st.error(f"Backend offline: {detail}")
    st.stop()

st.markdown(
    "Manage protocols, reagents, and dose-response experiments from a single place. "
    "Select an option below to get started."
)

st.markdown("---")

col1, col2 = st.columns(2)

with col1:
    if st.button("🧫 Add Reagent", use_container_width=True, type="primary"):
        st.switch_page("pages/add_reagent.py")
    if st.button("➕ Add Protocol", use_container_width=True, type="primary"):
        st.switch_page("pages/add_protocol.py")
    if st.button("🔬 Add Experiment", use_container_width=True, type="primary"):
        st.switch_page("pages/add_experiment.py")

with col2:
    if st.button("🔍 Search Reagents", use_container_width=True):
        st.switch_page("pages/search_reagents.py")
    if st.button("🔍 Search Protocols", use_container_width=True):
        st.switch_page("pages/search_protocols.py")
    if st.button("📋 Search Experiments", use_container_width=True):
        st.switch_page("pages/search_experiments.py")

st.markdown("---")
if st.button("⚖️ Compare Experiments", use_container_width=True):
    st.switch_page("pages/compare_experiments.py")
