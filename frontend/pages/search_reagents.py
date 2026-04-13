"""
Search Reagents page.

Searches reagent catalog entries by partial name and/or manufacturer match (case-insensitive).
Each result row is expandable to show registered batches for that reagent, with an inline form
to register a new batch.

API: GET /api/reagent-catalogs/search
     GET /api/reagent-batches?reagentId={id}
     POST /api/reagent-batches
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import datetime
import requests
import streamlit as st
from utils import check_auth, format_date, get_auth_headers, resolve_backend_url, show_persistent_error, show_stored_errors, translate_error

check_auth()
BACKEND_URL = resolve_backend_url()

if st.button("← Back to Dashboard"):
    st.switch_page("pages/dashboard.py")

st.title("Search Reagents")
show_stored_errors("search_reagents")
st.markdown("---")

# ---------------------------------------------------------------------------
# Search form
# ---------------------------------------------------------------------------

with st.form("search_reagent_form"):
    col1, col2, col3 = st.columns([3, 3, 1])
    with col1:
        name_filter = st.text_input("Name contains", placeholder="Leave blank to skip")
    with col2:
        mfr_filter = st.text_input("Manufacturer contains", placeholder="Leave blank to skip")
    with col3:
        page_size = st.selectbox("Page size", [10, 20, 50], index=1)
    submitted = st.form_submit_button("Search", use_container_width=True)

st.session_state.setdefault("reagent_page", 0)

if submitted or "reagent_results" in st.session_state:
    if submitted:
        st.session_state["reagent_page"] = 0

    params = {
        "page": st.session_state.get("reagent_page", 0),
        "size": page_size,
    }
    if name_filter and name_filter.strip():
        params["name"] = name_filter.strip()
    if mfr_filter and mfr_filter.strip():
        params["manufacturer"] = mfr_filter.strip()

    try:
        resp = requests.get(
            f"{BACKEND_URL}/api/reagent-catalogs/search",
            params=params,
            headers=get_auth_headers(),
            timeout=10,
        )
        if resp.status_code == 200:
            st.session_state["reagent_results"] = resp.json()
        else:
            show_persistent_error(translate_error(f"Search failed (HTTP {resp.status_code})"), key="search_reagents")
            st.session_state.pop("reagent_results", None)
    except requests.exceptions.RequestException as e:
        show_persistent_error(translate_error(str(e)), key="search_reagents")
        st.session_state.pop("reagent_results", None)

results = st.session_state.get("reagent_results")
if results is None:
    st.stop()

# ---------------------------------------------------------------------------
# Results list
# ---------------------------------------------------------------------------

content = results.get("content", [])
total = results.get("totalElements", 0)
cur_page = results.get("number", 0)
total_pages = results.get("totalPages", 0)

if not content:
    st.info("No reagents found.")
    st.stop()

st.caption(f"{total} total — page {cur_page + 1} of {total_pages or 1}")

for reagent in content:
    reagent_id = reagent.get("id")
    with st.container(border=True):
        c1, c2, c3 = st.columns([3, 3, 4])
        c1.markdown(f"**{reagent.get('name')}**")
        c2.caption(reagent.get("manufacturer") or "—")
        c3.caption(reagent.get("description") or "")

        with st.expander("🧪 Reagent Batches", expanded=False):
            _batch_reload_key = f"reload_batches_{reagent_id}"

            # Load batches (re-fetch if triggered by a new-lot creation)
            try:
                br = requests.get(
                    f"{BACKEND_URL}/api/reagent-batches",
                    params={"reagentId": reagent_id},
                    headers=get_auth_headers(),
                    timeout=10,
                )
                batches = br.json() if br.status_code == 200 else []
            except requests.exceptions.RequestException:
                batches = []

            # ── Batch table ──────────────────────────────────────────────
            if batches:
                today = datetime.date.today()
                rows = []
                for b in batches:
                    lot = b.get("lotNumber", "—")
                    exp_str = b.get("expiryDate")
                    supplier = b.get("supplier") or "—"
                    notes = b.get("notes") or "—"

                    if exp_str:
                        try:
                            exp_date = datetime.date.fromisoformat(exp_str)
                            days_left = (exp_date - today).days
                            if days_left < 0:
                                expiry_label = f"{format_date(exp_str)} ⚠️ EXPIRED"
                            elif days_left <= 30:
                                expiry_label = f"{format_date(exp_str)} ⏰ {days_left}d left"
                            else:
                                expiry_label = format_date(exp_str)
                        except ValueError:
                            expiry_label = exp_str
                    else:
                        expiry_label = "—"

                    rows.append({
                        "Lot Number": lot,
                        "Expiry Date": expiry_label,
                        "Supplier": supplier,
                        "Notes": notes,
                    })

                st.dataframe(rows, use_container_width=True, hide_index=True)
            else:
                st.info("No batches registered yet.")

            # ── Register new batch ───────────────────────────────────────
            st.markdown("**Register a new batch**")
            with st.form(f"new_batch_form_{reagent_id}", clear_on_submit=True):
                fc1, fc2, fc3 = st.columns([3, 2, 3])
                new_lot = fc1.text_input(
                    "Lot Number *", placeholder="e.g. LOT-2026-001",
                    key=f"new_lot_{reagent_id}"
                )
                new_expiry = fc2.date_input(
                    "Expiry Date *", value=None,
                    key=f"new_expiry_{reagent_id}"
                )
                new_supplier = fc3.text_input(
                    "Supplier (optional)", placeholder="e.g. ThermoFisher",
                    key=f"new_supplier_{reagent_id}"
                )
                new_notes = st.text_input(
                    "Notes (optional)", placeholder="e.g. Keep at -20°C",
                    key=f"new_notes_{reagent_id}"
                )
                register_btn = st.form_submit_button(
                    "Register Batch", type="primary", use_container_width=True
                )

            if register_btn:
                if not new_lot.strip():
                    show_persistent_error("Lot Number is required.")
                elif new_expiry is None:
                    show_persistent_error("Expiry Date is required.")
                else:
                    payload = {
                        "reagentId": reagent_id,
                        "lotNumber": new_lot.strip(),
                        "expiryDate": new_expiry.isoformat(),
                        "supplier": new_supplier.strip() or None,
                        "notes": new_notes.strip() or None,
                    }
                    try:
                        post_r = requests.post(
                            f"{BACKEND_URL}/api/reagent-batches",
                            json=payload,
                            headers=get_auth_headers(),
                            timeout=10,
                        )
                        if post_r.status_code == 201:
                            st.success(
                                f"Batch **{new_lot.strip()}** registered for "
                                f"**{reagent.get('name')}**."
                            )
                            st.rerun()
                        else:
                            detail = post_r.json().get("message", post_r.text)
                            show_persistent_error(translate_error(detail), key="search_reagents")
                    except requests.exceptions.RequestException as e:
                        show_persistent_error(translate_error(str(e)), key="search_reagents")

# ---------------------------------------------------------------------------
# Pagination
# ---------------------------------------------------------------------------

if total_pages > 1:
    st.markdown("---")
    nav = st.columns([1, 1])
    with nav[0]:
        if cur_page > 0 and st.button("← Previous", use_container_width=True):
            st.session_state["reagent_page"] = cur_page - 1
            st.rerun()
    with nav[1]:
        if cur_page < total_pages - 1 and st.button("Next →", use_container_width=True):
            st.session_state["reagent_page"] = cur_page + 1
            st.rerun()
