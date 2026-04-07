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
from utils import check_auth, inject_global_css, render_logo, render_sidebar, resolve_backend_url

BACKEND_URL = resolve_backend_url()

check_auth()

st.set_page_config(page_title="Add Reagent", page_icon="🧫", layout="wide")

inject_global_css()

_ASSETS = os.path.join(os.path.dirname(__file__), "..", "..", "assets")
render_logo(_ASSETS)
render_sidebar(BACKEND_URL)

if st.button("← Back to Dashboard"):
    st.switch_page("app.py")

st.title("New Reagent")
st.markdown("---")

with st.form("reagent_form"):
    name = st.text_input("Name", placeholder="e.g. Anti-Human IgG")
    manufacturer = st.text_input("Manufacturer", placeholder="e.g. Sigma-Aldrich")
    description = st.text_area("Description", placeholder="Optional notes")
    submitted = st.form_submit_button("Add Reagent", type="primary", use_container_width=True)
    if submitted:
        if not name.strip() or not manufacturer.strip():
            st.error("Name and Manufacturer are required.")
        else:
            payload = {
                "name": name.strip(),
                "manufacturer": manufacturer.strip(),
                "description": description.strip(),
            }
            try:
                resp = requests.post(f"{BACKEND_URL}/api/reagent-catalogs", json=payload, timeout=10)
                if resp.status_code == 201:
                    data = resp.json()
                    st.success(f"Reagent added — ID {data['id']}")
                else:
                    detail = resp.json().get("message", resp.text)
                    st.error(f"Failed ({resp.status_code}): {detail}")
            except requests.exceptions.RequestException as e:
                st.error(f"Request failed: {e}")
