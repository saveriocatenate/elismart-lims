"""
Experiment Details page.

Read-only view of a single experiment: metadata (name, protocol, date, status),
measurement pairs table, and used reagent batches table.
The experiment ID is passed via st.session_state["selected_exp_id"].
API: GET /api/experiments/{id}
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

st.set_page_config(page_title="Experiment Details", page_icon="🔬", layout="wide")

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

if st.button("← Back to Search"):
    del st.session_state["selected_exp_id"]
    st.switch_page("pages/search_experiments.py")

exp_id = st.session_state.get("selected_exp_id")
if not exp_id:
    st.warning("No experiment selected.")
    st.stop()

st.title("Experiment Details")
st.markdown("---")

try:
    resp = requests.get(f"{BACKEND_URL}/api/experiments/{exp_id}", timeout=10)
    if resp.status_code != 200:
        st.error(f"Failed to load (HTTP {resp.status_code})")
        st.stop()
    data = resp.json()
except requests.exceptions.RequestException as e:
    st.error(f"Request failed: {e}")
    st.stop()

c1, c2, c3, c4 = st.columns(4)
c1.metric("Status", data.get("status", "—"))
c2.markdown(f"**Protocol**\n\n{data.get('protocolName', '—')}")
c3.markdown(f"**Date**\n\n{data.get('date', '').replace('T', ' ') if data.get('date') else '—'}")
c4.markdown(f"**Experiment**\n\n{data.get('name', '—')}")

st.markdown("---")

pairs = data.get("measurementPairs", [])
st.subheader("Measurement Pairs", divider="gray")
if pairs:
    st.dataframe(
        [
            {
                "Type": p.get("pairType"),
                "Nominal Conc.": p.get("concentrationNominal"),
                "Signal 1": p.get("signal1"),
                "Signal 2": p.get("signal2"),
                "Mean": p.get("signalMean"),
                "%CV": p.get("cvPct"),
                "%Recovery": p.get("recoveryPct"),
                "Outlier": p.get("isOutlier"),
            }
            for p in pairs
        ],
        use_container_width=True,
        hide_index=True,
    )
else:
    st.info("No measurement pairs.")

batches = data.get("usedReagentBatches", [])
st.subheader("Reagent Batches Used", divider="gray")
if batches:
    st.dataframe(
        [
            {
                "Reagent": b.get("reagentName"),
                "Lot": b.get("lotNumber"),
                "Expiry": b.get("expiryDate"),
            }
            for b in batches
        ],
        use_container_width=True,
        hide_index=True,
    )
else:
    st.info("No reagent batches.")
