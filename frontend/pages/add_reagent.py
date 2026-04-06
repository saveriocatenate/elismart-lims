"""
Add Reagent page.

Simple form to add a single reagent to the catalog (name, manufacturer, optional description).
API: POST /api/reagent-catalogs
"""
import os
import base64
import requests
import streamlit as st


def _resolve_backend_url():
    env = os.environ.get("BACKEND_URL")
    if env:
        return env
    try:
        return st.secrets.get("backend_url", "http://localhost:8080")
    except Exception:
        return "http://localhost:8080"

BACKEND_URL = _resolve_backend_url()


def _check_auth():
    if st.session_state.get("authenticated", False):
        return True
    st.stop()

_check_auth()

st.set_page_config(page_title="Add Reagent", page_icon="🧫", layout="wide")

LOGO_PATH = os.path.join(os.path.dirname(__file__), "..", "..", "assets", "EliSmartLogo.png")
if os.path.exists(LOGO_PATH):
    with open(LOGO_PATH, "rb") as f:
        logo_b64 = base64.b64encode(f.read()).decode()
    st.markdown(
        f'<div style="text-align:center; margin-bottom:0.5rem">'
        f'<img src="data:image/png;base64,{logo_b64}" style="max-width:200px; height:auto" />'
        f'</div>',
        unsafe_allow_html=True,
    )

with st.sidebar:
    st.caption(f"🔗 Backend: `{BACKEND_URL}`")
    if st.button("🚪 Logout", use_container_width=True):
        st.session_state["authenticated"] = False
        st.rerun()

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
