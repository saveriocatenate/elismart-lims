"""
EliSmart LIMS — Application entry point.

Handles authentication and defines the navigation structure with sections.
Common page setup (CSS palette, logo, sidebar) runs once here before each page render.
API: GET /api/health (via dashboard.py)
"""
import os
import bcrypt
import streamlit as st

from utils import inject_global_css, render_logo, render_sidebar, resolve_backend_url

BACKEND_URL = resolve_backend_url()
_ASSETS = os.path.join(os.path.dirname(__file__), "..", "assets")


# ---------------------------------------------------------------------------
# Auth gate
# ---------------------------------------------------------------------------

def _get_creds():
    """Return (username, bcrypt_hash) from secrets or environment variables."""
    try:
        u = st.secrets.get("login_user", "")
        p = st.secrets.get("login_pass", "")
    except Exception:
        u, p = "", ""
    return u or os.environ.get("LOGIN_USER", ""), p or os.environ.get("LOGIN_PASS", "")


def _check_login():
    """Show a login gate if the user is not authenticated."""
    if st.session_state.get("authenticated", False):
        return
    expected_user, expected_pass = _get_creds()
    st.set_page_config(page_title="EliSmart LIMS", page_icon="🧪")
    st.title("Accesso riservato")
    with st.form("login_form"):
        user = st.text_input("Username", autocomplete="username", label_visibility="collapsed")
        pwd = st.text_input(
            "Password", type="password",
            autocomplete="current-password", label_visibility="collapsed",
        )
        if st.form_submit_button("🔑 Accedi", use_container_width=True):
            try:
                password_matches = bcrypt.checkpw(
                    pwd.encode("utf-8"), expected_pass.encode("utf-8")
                )
            except Exception:
                password_matches = False
            if user == expected_user and password_matches:
                st.session_state["authenticated"] = True
                st.rerun()
            st.error("Credenziali non valide")
    st.stop()


_check_login()

# ---------------------------------------------------------------------------
# Global page config (once, before navigation)
# ---------------------------------------------------------------------------

st.set_page_config(page_title="EliSmart LIMS", page_icon="🧪", layout="wide")
inject_global_css()
render_logo(_ASSETS)
render_sidebar(BACKEND_URL)

# ---------------------------------------------------------------------------
# Navigation with sections
# ---------------------------------------------------------------------------

pg = st.navigation(
    {
        "": [
            st.Page("pages/dashboard.py", title="Dashboard", icon="🧪", default=True),
        ],
        "ADD": [
            st.Page("pages/add_reagent.py", title="Add Reagent", icon="🧫"),
            st.Page("pages/add_protocol.py", title="Add Protocol", icon="➕"),
            st.Page("pages/add_experiment.py", title="Add Experiment", icon="🔬"),
        ],
        "SEARCH": [
            st.Page("pages/search_reagents.py", title="Search Reagents", icon="🔍"),
            st.Page("pages/search_protocols.py", title="Search Protocols", icon="🔍"),
            st.Page("pages/search_experiments.py", title="Search Experiments", icon="📋"),
        ],
        "DETAILS": [
            st.Page("pages/protocol_details.py", title="Protocol Details", icon="📄"),
            st.Page("pages/experiment_details.py", title="Experiment Details", icon="🔬"),
        ],
        "AI INTEGRATION": [
            st.Page("pages/compare_experiments.py", title="Compare Experiments", icon="📊"),
        ],
    }
)

pg.run()
