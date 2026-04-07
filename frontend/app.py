"""
EliSmart LIMS — Dashboard (entry point).

Checks backend health on load and provides navigation buttons to all main pages.
Auth gate: validates username + bcrypt-hashed password from secrets.toml or environment variables.
API: GET /api/health

Button layout
-------------
Left column  : Add Reagent | Add Protocol | Add Experiment
Right column : Search Reagent (placeholder) | Search Protocol | Search Experiment
Full width   : Compare Experiments
"""
import os
import bcrypt
import requests
import streamlit as st

from utils import check_auth, inject_global_css, render_logo, render_sidebar, resolve_backend_url

BACKEND_URL = resolve_backend_url()


def _get_creds():
    """Return (username, bcrypt_hash) from secrets or environment variables.

    secrets.toml must store ``login_pass`` as a bcrypt hash, not plaintext.
    Generate one with:  python -c "import bcrypt; print(bcrypt.hashpw(b'yourpassword', bcrypt.gensalt()).decode())"
    """
    try:
        u = st.secrets.get("login_user", "")
        p = st.secrets.get("login_pass", "")
    except Exception:
        u, p = "", ""
    return u or os.environ.get("LOGIN_USER", ""), p or os.environ.get("LOGIN_PASS", "")


def _check_login():
    """Show a login gate if the user is not authenticated. Returns when authenticated."""
    if st.session_state.get("authenticated", False):
        return
    expected_user, expected_pass = _get_creds()
    st.set_page_config(page_title="EliSmart LIMS", page_icon="🧪")
    st.title("Accesso riservato")
    with st.form("login_form"):
        user = st.text_input("Username", autocomplete="username", label_visibility="collapsed")
        pwd = st.text_input("Password", type="password", autocomplete="current-password", label_visibility="collapsed")
        if st.form_submit_button("🔑 Accedi", use_container_width=True):
            try:
                password_matches = bcrypt.checkpw(pwd.encode("utf-8"), expected_pass.encode("utf-8"))
            except Exception:
                password_matches = False
            if user == expected_user and password_matches:
                st.session_state["authenticated"] = True
                st.rerun()
            st.error("Credenziali non valide")
    st.stop()


_check_login()

st.set_page_config(page_title="EliSmart LIMS", page_icon="🧪", layout="wide")

inject_global_css()

# --- Header ---
_ASSETS = os.path.join(os.path.dirname(__file__), "..", "assets")
render_logo(_ASSETS)

# --- Sidebar ---
render_sidebar(BACKEND_URL)

# --- Page content ---
st.title("Dashboard")


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
