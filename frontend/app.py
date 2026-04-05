import requests
import streamlit as st

BACKEND_URL = st.secrets.get("backend_url", "http://localhost:8080")

st.set_page_config(page_title="EliSmart LIMS", page_icon="🧪")

st.title("EliSmart LIMS")

if st.button("Check Backend"):
    try:
        response = requests.get(f"{BACKEND_URL}/api/health", timeout=5)
        if response.status_code == 200:
            data = response.json()
            st.success(f"Backend is UP (timestamp: {data.get('timestamp', 'unknown')})")
        else:
            st.error(f"Backend returned status {response.status_code}")
    except requests.exceptions.ConnectionError:
        st.error("Cannot reach backend. Is the Spring Boot application running?")
    except requests.exceptions.Timeout:
        st.error("Backend request timed out.")
