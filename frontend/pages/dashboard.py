"""
Dashboard page.

Checks backend health on load, shows reagent expiry alerts, summary statistics,
and provides navigation buttons to all main pages.
API: GET /api/health
API: GET /api/reagent-batches/expiring?daysAhead=90
API: GET /api/protocols (for total count)
API: POST /api/experiments/search (for total and monthly OK/KO counts)
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import datetime
import requests
import streamlit as st
from utils import check_auth, get_auth_headers, resolve_backend_url, show_persistent_error, show_stored_errors, translate_error

check_auth()
BACKEND_URL = resolve_backend_url()

_EXPIRY_DAYS = 90


def _check_backend():
    """Return (healthy: bool, detail: str | dict)."""
    try:
        resp = requests.get(f"{BACKEND_URL}/api/health", headers=get_auth_headers(), timeout=5)
        if resp.status_code == 200:
            return True, resp.json()
        return False, f"Backend returned status {resp.status_code}"
    except requests.exceptions.ConnectionError:
        return False, "Cannot reach backend. Is the Spring Boot application running?"
    except requests.exceptions.Timeout:
        return False, "Backend request timed out."


@st.cache_data(ttl=60)
def _load_summary_stats(backend_url: str, _token: str) -> dict:
    """Fetch summary counts for protocols, experiments, and monthly OK/KO totals."""
    stats: dict = {"protocols": None, "experiments": None, "ok_month": None, "ko_month": None}
    headers = get_auth_headers()

    try:
        r = requests.get(
            f"{backend_url}/api/protocols",
            params={"size": 1},
            headers=headers,
            timeout=5,
        )
        if r.status_code == 200:
            stats["protocols"] = r.json().get("totalElements")
    except Exception:
        pass

    today = datetime.date.today()
    month_start = today.replace(day=1)

    for key, status in [("experiments", None), ("ok_month", "OK"), ("ko_month", "KO")]:
        try:
            payload: dict = {"size": 1}
            if status:
                payload["status"] = status
                payload["dateFrom"] = month_start.isoformat() + "T00:00:00"
                payload["dateTo"] = today.isoformat() + "T23:59:59"
            r = requests.post(
                f"{backend_url}/api/experiments/search",
                json=payload,
                headers=headers,
                timeout=5,
            )
            if r.status_code == 200:
                stats[key] = r.json().get("totalElements")
        except Exception:
            pass

    return stats


@st.cache_data(ttl=300)
def _load_expiring_batches(backend_url: str, days_ahead: int, _token: str):
    """Fetch expiring reagent batch alerts from the backend."""
    try:
        resp = requests.get(
            f"{backend_url}/api/reagent-batches/expiring",
            params={"daysAhead": days_ahead},
            headers=get_auth_headers(),
            timeout=10,
        )
        if resp.status_code == 200:
            return resp.json()
    except Exception:
        pass
    return []


def _render_expiry_alerts(alerts: list):
    """Render color-coded expiry alert boxes."""
    red = [a for a in alerts if a["daysUntilExpiry"] <= 30]
    yellow = [a for a in alerts if 31 <= a["daysUntilExpiry"] <= 60]
    gray = [a for a in alerts if 61 <= a["daysUntilExpiry"] <= 90]

    if not alerts:
        return

    st.markdown("### Allerte Scadenza Reagenti")

    for alert in red:
        st.error(
            f"🔴 **{alert['reagentName']}** ({alert['manufacturer']}) — "
            f"Lotto: `{alert['lotNumber']}` — "
            f"Scade: **{alert['expiryDate']}** ({alert['daysUntilExpiry']}gg rimanenti)"
        )

    for alert in yellow:
        st.warning(
            f"🟡 **{alert['reagentName']}** ({alert['manufacturer']}) — "
            f"Lotto: `{alert['lotNumber']}` — "
            f"Scade: {alert['expiryDate']} ({alert['daysUntilExpiry']}gg rimanenti)"
        )

    for alert in gray:
        st.info(
            f"⚪ **{alert['reagentName']}** ({alert['manufacturer']}) — "
            f"Lotto: `{alert['lotNumber']}` — "
            f"Scade: {alert['expiryDate']} ({alert['daysUntilExpiry']}gg rimanenti)"
        )

    st.markdown("---")


st.title("Dashboard")
show_stored_errors("dashboard")

healthy, detail = _check_backend()
if healthy:
    st.success(f"Backend online — {detail.get('timestamp', '')}")
else:
    show_persistent_error(translate_error(f"Backend offline: {detail}"), key="dashboard")
    st.stop()

token = st.session_state.get("jwt_token", "")

# ── Summary stats ────────────────────────────────────────────────────────────
stats = _load_summary_stats(BACKEND_URL, token)
st.markdown("### Riepilogo")
m1, m2, m3, m4 = st.columns(4)
m1.metric("Protocolli", stats["protocols"] if stats["protocols"] is not None else "—")
m2.metric("Esperimenti totali", stats["experiments"] if stats["experiments"] is not None else "—")
m3.metric("OK (mese corrente)", stats["ok_month"] if stats["ok_month"] is not None else "—")
m4.metric("KO (mese corrente)", stats["ko_month"] if stats["ko_month"] is not None else "—")
st.markdown("---")

# ── Reagent expiry alerts ─────────────────────────────────────────────────────
alerts = _load_expiring_batches(BACKEND_URL, _EXPIRY_DAYS, token)
_render_expiry_alerts(alerts)

st.markdown(
    "Gestisci protocolli, reagenti ed esperimenti dose-risposta da un unico pannello. "
    "Seleziona un'opzione qui sotto per iniziare."
)

st.markdown("---")

col1, col2 = st.columns(2)

with col1:
    if st.button("🧫 Nuovo Reagente", use_container_width=True, type="primary"):
        st.switch_page("pages/add_reagent.py")
    if st.button("➕ Nuovo Protocollo", use_container_width=True, type="primary"):
        st.switch_page("pages/add_protocol.py")
    if st.button("🔬 Nuovo Esperimento", use_container_width=True, type="primary"):
        st.switch_page("pages/add_experiment.py")

with col2:
    if st.button("🔍 Cerca Reagenti", use_container_width=True):
        st.switch_page("pages/search_reagents.py")
    if st.button("🔍 Cerca Protocolli", use_container_width=True):
        st.switch_page("pages/search_protocols.py")
    if st.button("📋 Cerca Esperimenti", use_container_width=True):
        st.switch_page("pages/search_experiments.py")

st.markdown("---")
if st.button("⚖️ Confronta Esperimenti", use_container_width=True):
    st.switch_page("pages/compare_experiments.py")
