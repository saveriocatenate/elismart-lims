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

st.set_page_config(page_title="Add Protocol", page_icon="🧪", layout="wide")

# --- Shared header ---
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

# --- Page ---
if st.button("← Back to Dashboard"):
    st.switch_page("app.py")

st.title("New Protocol")
st.markdown("---")

with st.form("protocol_form"):
    name = st.text_input("Name", placeholder="e.g. IgG Test Protocol")
    col1, col2, col3 = st.columns(3)
    with col1:
        num_cal = st.number_input("Calibration Pairs", min_value=1, value=5, step=1)
    with col2:
        num_ctrl = st.number_input("Control Pairs", min_value=1, value=3, step=1)
    with col3:
        max_cv = st.number_input("Max CV (%)", min_value=0.0, step=0.5, value=10.0, format="%.1f")
    max_error = st.number_input("Max Error Allowed (%)", min_value=0.0, step=0.5, value=15.0, format="%.1f")

    submitted = st.form_submit_button("Create Protocol", type="primary", use_container_width=True)
    if submitted:
        if not name.strip():
            st.error("Name is required.")
        else:
            payload = {
                "name": name.strip(),
                "numCalibrationPairs": num_cal,
                "numControlPairs": num_ctrl,
                "maxCvAllowed": max_cv,
                "maxErrorAllowed": max_error,
            }
            try:
                resp = requests.post(f"{BACKEND_URL}/api/protocols", json=payload, timeout=10)
                if resp.status_code == 201:
                    data = resp.json()
                    st.success(f"Protocol created — ID {data['id']}")
                else:
                    detail = resp.json().get("detail", resp.text)
                    st.error(f"Failed ({resp.status_code}): {detail}")
            except requests.exceptions.RequestException as e:
                st.error(f"Request failed: {e}")
