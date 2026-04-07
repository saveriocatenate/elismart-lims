"""
EliSmart LIMS — Shared page utilities.

Provides the global CSS colour palette, logo renderer, sidebar renderer, and common
bootstrap helpers used by every page in the application. Import this module at the
top of each page *before* calling any Streamlit widget.

Usage (from pages/)::

    import sys, os
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    from utils import check_auth, inject_global_css, render_logo, render_sidebar, BACKEND_URL
"""
import os
import base64
import datetime
import streamlit as st

# ---------------------------------------------------------------------------
# Backend URL
# ---------------------------------------------------------------------------

def resolve_backend_url() -> str:
    """Return the backend base URL from the ``BACKEND_URL`` env var or ``secrets.toml``."""
    env = os.environ.get("BACKEND_URL")
    if env:
        return env
    try:
        return st.secrets.get("backend_url", "http://localhost:8080")
    except Exception:
        return "http://localhost:8080"


# ---------------------------------------------------------------------------
# Auth gate
# ---------------------------------------------------------------------------

def check_auth() -> None:
    """Stop rendering the current page if the user is not authenticated."""
    if not st.session_state.get("authenticated", False):
        st.stop()


# ---------------------------------------------------------------------------
# Date formatting
# ---------------------------------------------------------------------------

def format_date(iso_str: str | None) -> str:
    """Format an ISO datetime or date string into ``dd/mm/yyyy`` for display.

    Parameters
    ----------
    iso_str:
        A string in ISO-8601 format (e.g. ``"2026-04-07T09:00:00"`` or ``"2026-04-07"``).
        ``None`` or empty strings return ``"—"``.
    """
    if not iso_str:
        return "—"
    try:
        date_part = iso_str[:10]  # "YYYY-MM-DD"
        d = datetime.date.fromisoformat(date_part)
        return d.strftime("%d/%m/%Y")
    except (ValueError, AttributeError):
        return iso_str or "—"


# ---------------------------------------------------------------------------
# Global CSS palette
# ---------------------------------------------------------------------------

_GLOBAL_CSS = """
<style>
/* ===  EliSmart LIMS — Green Palette  === */

/* Primary buttons (Add / Create / Submit) — dark green */
.stButton > button[kind="primary"] {
    background-color: #2E7D32;
    color: white;
    border: none;
}
.stButton > button[kind="primary"]:hover {
    background-color: #388E3C;
    color: white;
}
.stButton > button[kind="primary"]:active {
    background-color: #1B5E20;
    color: white;
}

/* Primary form-submit buttons (st.form_submit_button with type="primary") */
.stFormSubmitButton > button[kind="primaryFormSubmit"] {
    background-color: #2E7D32;
    color: white;
    border: none;
}
.stFormSubmitButton > button[kind="primaryFormSubmit"]:hover {
    background-color: #388E3C;
    color: white;
}

/* Secondary buttons (Search / Navigate / Back) — outlined green */
.stButton > button[kind="secondary"] {
    color: #2E7D32;
    border: 1.5px solid #2E7D32;
    background-color: #FFFFFF;
}
.stButton > button[kind="secondary"]:hover {
    background-color: #E8F5E9;
    border-color: #388E3C;
    color: #1B5E20;
}

/* Secondary form-submit buttons */
.stFormSubmitButton > button[kind="secondaryFormSubmit"] {
    color: #2E7D32;
    border: 1.5px solid #2E7D32;
    background-color: #FFFFFF;
}
.stFormSubmitButton > button[kind="secondaryFormSubmit"]:hover {
    background-color: #E8F5E9;
}

/* Sidebar logout button — red
   The sidebar contains only the logout button, so this selector is safe. */
[data-testid="stSidebar"] .stButton > button {
    background-color: #C62828;
    color: white;
    border: none;
}
[data-testid="stSidebar"] .stButton > button:hover {
    background-color: #D32F2F;
    color: white;
}

/* Delete/danger buttons.
   Usage: place  st.markdown('<div class="delete-btn"></div>', unsafe_allow_html=True)
   immediately before the st.button call, then the adjacent stButton picks up the red style. */
[data-testid="stMarkdown"]:has(> div.delete-btn) + [data-testid="stButton"] > button {
    background-color: #C62828;
    color: white;
    border: none;
}
[data-testid="stMarkdown"]:has(> div.delete-btn) + [data-testid="stButton"] > button:hover {
    background-color: #D32F2F;
    color: white;
}

/* Confirmation dialog primary button — red.
   Inside @st.dialog the destructive action should appear in red. */
[data-testid="stModal"] .stButton > button[kind="primary"],
[data-testid="stDialog"] .stButton > button[kind="primary"] {
    background-color: #C62828;
    color: white;
    border: none;
}
[data-testid="stModal"] .stButton > button[kind="primary"]:hover,
[data-testid="stDialog"] .stButton > button[kind="primary"]:hover {
    background-color: #D32F2F;
    color: white;
}

/* Multiselect selected-item tags — green background, white text */
.stMultiSelect [data-baseweb="tag"] {
    background-color: #2E7D32 !important;
}
.stMultiSelect [data-baseweb="tag"] span {
    color: white !important;
    max-width: none !important;
    white-space: normal !important;
}
.stMultiSelect [data-baseweb="tag"] [aria-label*="Delete"] svg {
    fill: white !important;
}
</style>
"""


def inject_global_css() -> None:
    """Inject the project-wide CSS palette into the current page.

    Call this once per page, before any widget rendering.
    """
    st.markdown(_GLOBAL_CSS, unsafe_allow_html=True)


# ---------------------------------------------------------------------------
# Layout helpers
# ---------------------------------------------------------------------------

def render_logo(assets_dir: str) -> None:
    """Render the EliSmart logo centred at the top of the page.

    Parameters
    ----------
    assets_dir:
        Absolute path to the ``assets/`` directory that contains
        ``EliSmartLogo.png``.
    """
    logo_path = os.path.join(assets_dir, "EliSmartLogo.png")
    if os.path.exists(logo_path):
        with open(logo_path, "rb") as f:
            b64 = base64.b64encode(f.read()).decode()
        st.markdown(
            f'<div style="text-align:center; margin-bottom:0.5rem">'
            f'<img src="data:image/png;base64,{b64}" style="max-width:200px; height:auto" />'
            f'</div>',
            unsafe_allow_html=True,
        )


def render_sidebar(backend_url: str) -> None:
    """Render the standard sidebar with the backend URL caption and logout button.

    Parameters
    ----------
    backend_url:
        The backend base URL to display in the sidebar caption.
    """
    with st.sidebar:
        st.caption(f"🔗 Backend: `{backend_url}`")
        if st.button("🚪 Logout", use_container_width=True):
            st.session_state["authenticated"] = False
            st.rerun()
