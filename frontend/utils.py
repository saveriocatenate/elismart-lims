"""
EliSmart LIMS — Shared page utilities.

Provides the global CSS colour palette, logo renderer, sidebar renderer, and common
bootstrap helpers used by every page in the application. Import this module at the
top of each page *before* calling any Streamlit widget.

Usage (from pages/)::

    import sys, os
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    from utils import check_auth, get_auth_headers, inject_global_css, render_logo, render_sidebar, BACKEND_URL
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
# Auth helpers
# ---------------------------------------------------------------------------

def check_auth() -> None:
    """Stop rendering the current page if the user is not authenticated.

    Checks for a non-empty JWT token in ``st.session_state["jwt_token"]``.
    If absent or empty, stops execution — the login gate in ``app.py`` will
    intercept the rerun and render the login form instead.
    """
    if not st.session_state.get("jwt_token"):
        st.stop()


def get_auth_headers() -> dict[str, str]:
    """Return the ``Authorization`` header dict for authenticated API calls.

    Returns
    -------
    dict
        ``{"Authorization": "Bearer <token>"}`` using the JWT stored in
        session state, or an empty Bearer value if the token is missing.
    """
    token = st.session_state.get("jwt_token", "")
    return {"Authorization": f"Bearer {token}"}


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
# QC colour coding
# ---------------------------------------------------------------------------

def color_code_qc(value: float | None, limit: float | None, metric_type: str) -> str:
    """Return a CSS background-color string for a QC metric cell.

    Traffic-light thresholds:

    - ``"cv"``: green if *value* ≤ *limit*, yellow if ≤ *limit* × 1.5, red otherwise.
    - ``"recovery"``: green if *value* is within *100 ± limit*, yellow if within
      *100 ± limit × 1.5*, red otherwise.

    Returns ``""`` (no colouring) when *value* or *limit* is ``None``.

    Parameters
    ----------
    value:
        The metric value to evaluate (e.g. cvPct or recoveryPct).
    limit:
        The protocol acceptance limit (maxCvAllowed or maxErrorAllowed).
    metric_type:
        Either ``"cv"`` or ``"recovery"``.
    """
    if value is None or limit is None:
        return ""

    if metric_type == "cv":
        if value <= limit:
            return "background-color: #C8E6C9"   # light green
        if value <= limit * 1.5:
            return "background-color: #FFF9C4"   # light yellow
        return "background-color: #FFCDD2"        # light red

    if metric_type == "recovery":
        deviation = abs(value - 100.0)
        if deviation <= limit:
            return "background-color: #C8E6C9"
        if deviation <= limit * 1.5:
            return "background-color: #FFF9C4"
        return "background-color: #FFCDD2"

    return ""


# ---------------------------------------------------------------------------
# Error display helpers
# ---------------------------------------------------------------------------

#: Maps known backend error substrings to user-friendly Italian messages.
ERROR_TRANSLATIONS: dict[str, str] = {
    "Duplicate entry": "Esiste già un elemento con lo stesso nome. Scegli un nome diverso.",
    "must not be blank": "Questo campo è obbligatorio.",
    "must be greater than": "Il valore deve essere maggiore di",
    "Connection refused": "Impossibile contattare il server. Verifica che il backend sia avviato.",
}


def translate_error(raw_message: str) -> str:
    """Map known backend error substrings to user-friendly Italian messages.

    Iterates :data:`ERROR_TRANSLATIONS` and returns the first matching
    translation.  Falls back to *raw_message* unchanged if no pattern matches.

    Parameters
    ----------
    raw_message:
        The raw error string returned by the backend or raised by a network
        exception.
    """
    for pattern, translation in ERROR_TRANSLATIONS.items():
        if pattern.lower() in raw_message.lower():
            return translation
    return raw_message


def show_persistent_error(message: str, key: str | None = None) -> None:
    """Display an error message and optionally persist it across a ``st.rerun()``.

    When *key* is provided the message is saved to ``st.session_state`` so
    that :func:`show_stored_errors` can re-display it after the next rerun.
    Without a key the function is equivalent to ``st.error(message)``.

    Parameters
    ----------
    message:
        The error text to display.
    key:
        Optional session-state key prefix.  If given, the message is stored
        under ``f"error_{key}"`` and survives a rerun.
    """
    if key:
        st.session_state[f"error_{key}"] = message
    st.error(message)


def show_stored_errors(key: str) -> None:
    """Display and clear any error previously stored by :func:`show_persistent_error`.

    Call this once near the top of each page that uses keyed errors so that
    errors survive a ``st.rerun()`` and remain visible on the next render.

    Parameters
    ----------
    key:
        The same string that was passed as *key* to :func:`show_persistent_error`.
    """
    error_key = f"error_{key}"
    if error_key in st.session_state:
        st.error(st.session_state.pop(error_key))


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
    """Render the standard sidebar with user info, backend URL, and logout button.

    Parameters
    ----------
    backend_url:
        The backend base URL to display in the sidebar caption.
    """
    with st.sidebar:
        username = st.session_state.get("username", "")
        role = st.session_state.get("role", "")
        if username:
            st.caption(f"👤 **{username}** ({role})")
        st.caption(f"🔗 Backend: `{backend_url}`")
        if st.button("🚪 Logout", use_container_width=True):
            st.session_state.pop("jwt_token", None)
            st.session_state.pop("username", None)
            st.session_state.pop("role", None)
            st.rerun()
