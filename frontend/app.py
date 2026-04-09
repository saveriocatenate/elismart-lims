"""
EliSmart LIMS — Application entry point.

Handles JWT-based authentication and defines the navigation structure with sections.
Common page setup (CSS palette, logo, sidebar) runs once here before each page render.
Auth API: POST /api/auth/login
"""
import os
import requests
import streamlit as st

from utils import inject_global_css, render_logo, render_sidebar, resolve_backend_url

BACKEND_URL = resolve_backend_url()
_ASSETS = os.path.join(os.path.dirname(__file__), "..", "assets")


# ---------------------------------------------------------------------------
# Auth gate
# ---------------------------------------------------------------------------

def _check_login():
    """Show a login form if the user does not have a valid JWT token in session."""
    if st.session_state.get("jwt_token"):
        return

    st.set_page_config(page_title="EliSmart LIMS", page_icon="🧪")
    st.title("Accesso riservato")

    with st.form("login_form"):
        user = st.text_input("Username", autocomplete="username", label_visibility="collapsed")
        pwd = st.text_input(
            "Password", type="password",
            autocomplete="current-password", label_visibility="collapsed",
        )
        if st.form_submit_button("🔑 Accedi", use_container_width=True):
            if not user.strip() or not pwd.strip():
                st.error("Inserisci username e password.")
                st.stop()
            try:
                resp = requests.post(
                    f"{BACKEND_URL}/api/auth/login",
                    json={"username": user.strip(), "password": pwd},
                    timeout=10,
                )
                if resp.status_code == 200:
                    data = resp.json()
                    st.session_state["jwt_token"] = data["token"]
                    st.session_state["username"] = data["username"]
                    st.session_state["role"] = data["role"]
                    st.rerun()
                elif resp.status_code == 401:
                    st.error("Credenziali non valide.")
                else:
                    st.error(f"Errore dal server ({resp.status_code}). Riprova.")
            except requests.exceptions.ConnectionError:
                st.error("Impossibile raggiungere il backend. Verificare che sia avviato.")
            except requests.exceptions.Timeout:
                st.error("Il backend non ha risposto in tempo. Riprova.")
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

_is_admin = st.session_state.get("role") == "ADMIN"

_nav: dict = {
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

if _is_admin:
    _nav["ADMIN"] = [
        st.Page("pages/user_management.py", title="User Management", icon="👥"),
    ]

pg = st.navigation(_nav)

pg.run()
