"""
User Management page — ADMIN only.

Displays all registered users with their role and status. Allows an ADMIN to:
- Create a new user (POST /api/auth/register)
- Change a user's role (PUT /api/users/{id}/role)
- Disable a user account (DELETE /api/users/{id})

Access is restricted to users whose session role is "ADMIN". Any other role sees an
"Accesso non autorizzato" message and is redirected to the dashboard.

API endpoints used:
  GET    /api/users
  PUT    /api/users/{id}/role
  DELETE /api/users/{id}
  POST   /api/auth/register
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import requests
import streamlit as st
from utils import check_auth, get_auth_headers, resolve_backend_url, show_confirmation_dialog, show_persistent_error, show_stored_errors, translate_error

check_auth()

# ADMIN-only guard
if st.session_state.get("role") != "ADMIN":
    show_persistent_error("Accesso non autorizzato. Questa pagina è riservata agli amministratori.")
    st.button("← Torna al Dashboard", on_click=lambda: st.switch_page("pages/dashboard.py"))
    st.stop()

BACKEND_URL = resolve_backend_url()
ROLES = ["ANALYST", "REVIEWER", "ADMIN"]

st.title("Gestione Utenti")
show_stored_errors("user_management")
st.markdown("---")

# ---------------------------------------------------------------------------
# Fetch user list
# ---------------------------------------------------------------------------

def fetch_users() -> list[dict]:
    """Fetch all users from the backend. Returns an empty list on error."""
    try:
        resp = requests.get(f"{BACKEND_URL}/api/users", headers=get_auth_headers(), timeout=10)
        if resp.status_code == 200:
            return resp.json()
        show_persistent_error(translate_error(f"Errore nel caricamento utenti ({resp.status_code}): {resp.text}"), key="user_management")
    except requests.exceptions.RequestException as exc:
        show_persistent_error(translate_error(f"Impossibile raggiungere il backend: {exc}"), key="user_management")
    return []


users = fetch_users()

# ---------------------------------------------------------------------------
# User table
# ---------------------------------------------------------------------------

st.subheader("Utenti registrati")

if not users:
    st.info("Nessun utente trovato.")
else:
    current_username = st.session_state.get("username", "")

    for user in users:
        uid = user["id"]
        uname = user["username"]
        role = user["role"]
        enabled = user["enabled"]
        is_self = uname == current_username

        status_label = "✅ Attivo" if enabled else "🚫 Disabilitato"
        self_label = " *(tu)*" if is_self else ""

        with st.expander(f"👤 {uname}{self_label} — {role} — {status_label}"):
            col_role, col_disable = st.columns([2, 1])

            # ---- Change role ----
            with col_role:
                new_role = st.selectbox(
                    "Ruolo",
                    options=ROLES,
                    index=ROLES.index(role),
                    key=f"role_select_{uid}",
                )
                if st.button("Aggiorna ruolo", key=f"btn_role_{uid}"):
                    try:
                        r = requests.put(
                            f"{BACKEND_URL}/api/users/{uid}/role",
                            json={"role": new_role},
                            headers=get_auth_headers(),
                            timeout=10,
                        )
                        if r.status_code == 200:
                            st.success(f"Ruolo di '{uname}' aggiornato a {new_role}.")
                            st.rerun()
                        else:
                            body = r.json() if r.headers.get("content-type", "").startswith("application/json") else {}
                            show_persistent_error(translate_error(body.get("message", f"Errore {r.status_code}")), key="user_management")
                    except requests.exceptions.RequestException as exc:
                        show_persistent_error(translate_error(f"Errore di rete: {exc}"), key="user_management")

            # ---- Disable user ----
            with col_disable:
                if enabled:
                    if is_self:
                        st.caption("Non puoi disabilitare il tuo stesso account.")
                    else:
                        st.markdown('<div class="delete-btn"></div>', unsafe_allow_html=True)
                        if st.button("Disabilita", key=f"btn_disable_{uid}"):
                            st.session_state[f"confirm_disable_{uid}"] = True

                        if show_confirmation_dialog(
                            f"Disabilitare l'utente **{uname}**? L'account non potrà più accedere al sistema.",
                            key=f"disable_{uid}",
                        ):
                            try:
                                r = requests.delete(
                                    f"{BACKEND_URL}/api/users/{uid}",
                                    headers=get_auth_headers(),
                                    timeout=10,
                                )
                                if r.status_code == 200:
                                    st.success(f"Utente '{uname}' disabilitato.")
                                    st.rerun()
                                else:
                                    body = r.json() if r.headers.get("content-type", "").startswith("application/json") else {}
                                    show_persistent_error(translate_error(body.get("message", f"Errore {r.status_code}")), key="user_management")
                            except requests.exceptions.RequestException as exc:
                                show_persistent_error(translate_error(f"Errore di rete: {exc}"), key="user_management")
                else:
                    st.caption("Account già disabilitato.")

st.markdown("---")

# ---------------------------------------------------------------------------
# Create new user
# ---------------------------------------------------------------------------

st.subheader("Crea nuovo utente")

with st.form("create_user_form", clear_on_submit=True):
    col1, col2, col3 = st.columns(3)
    with col1:
        new_username = st.text_input("Username", max_chars=50)
    with col2:
        new_password = st.text_input("Password", type="password")
    with col3:
        new_role = st.selectbox("Ruolo", options=ROLES)

    submitted = st.form_submit_button("Crea utente", type="primary", use_container_width=True)

if submitted:
    if not new_username.strip() or not new_password.strip():
        show_persistent_error("Username e password sono obbligatori.")
    else:
        try:
            r = requests.post(
                f"{BACKEND_URL}/api/auth/register",
                json={
                    "username": new_username.strip(),
                    "password": new_password,
                    "role": new_role,
                },
                headers=get_auth_headers(),
                timeout=10,
            )
            if r.status_code == 201:
                st.success(f"Utente '{new_username.strip()}' creato con ruolo {new_role}.")
                st.rerun()
            else:
                body = r.json() if r.headers.get("content-type", "").startswith("application/json") else {}
                show_persistent_error(translate_error(body.get("message", f"Errore {r.status_code}")), key="user_management")
        except requests.exceptions.RequestException as exc:
            show_persistent_error(translate_error(f"Errore di rete: {exc}"), key="user_management")
