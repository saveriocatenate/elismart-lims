"""
Tests for app.py — the main dashboard page.

Covers:
- Unauthenticated state renders the login form
- Invalid login credentials show an error
- Authenticated state with a healthy backend renders the dashboard
- Authenticated state with an unreachable backend shows an error and stops
"""
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
import requests as req
from streamlit.testing.v1 import AppTest

APP_FILE = Path(__file__).parent.parent / "app.py"
BACKEND_URL = "http://testserver:8080"


def _mock_healthy():
    """Return a mock response representing a healthy backend."""
    m = MagicMock()
    m.status_code = 200
    m.json.return_value = {"timestamp": "2026-04-06T10:00:00"}
    return m


# ---------------------------------------------------------------------------
# Auth gate
# ---------------------------------------------------------------------------

class TestAuthGate:
    def test_unauthenticated_shows_login_inputs(self):
        """Unauthenticated visit must render the login form (username + password)."""
        at = AppTest.from_file(str(APP_FILE))
        at.run()

        # Two text inputs: username and password
        assert len(at.text_input) >= 2

    def test_unauthenticated_shows_login_button(self):
        """Login submit button must be visible when not authenticated."""
        at = AppTest.from_file(str(APP_FILE))
        at.run()

        labels = [b.label for b in at.button]
        assert any("Accedi" in lbl for lbl in labels)

    def test_invalid_credentials_show_error(self):
        """Submitting wrong credentials must display an error message."""
        at = AppTest.from_file(str(APP_FILE))
        at.run()

        at.text_input[0].set_value("wronguser")
        at.text_input[1].set_value("wrongpass")
        at.button[0].click()
        at.run()

        assert len(at.error) > 0

    def test_dashboard_not_rendered_when_unauthenticated(self):
        """The Dashboard title must NOT appear before login."""
        at = AppTest.from_file(str(APP_FILE))
        at.run()

        titles = [t.value for t in at.title]
        assert "Dashboard" not in titles


# ---------------------------------------------------------------------------
# Authenticated — healthy backend
# ---------------------------------------------------------------------------

class TestDashboard:
    def test_dashboard_title_shown(self):
        """Authenticated users with a healthy backend see the Dashboard title."""
        with patch("requests.get", return_value=_mock_healthy()):
            at = AppTest.from_file(str(APP_FILE))
            at.session_state["authenticated"] = True
            at.run()

        assert at.title[0].value == "Dashboard"

    def test_backend_status_shown_as_success(self):
        """A 200 health response is displayed as a success message."""
        with patch("requests.get", return_value=_mock_healthy()):
            at = AppTest.from_file(str(APP_FILE))
            at.session_state["authenticated"] = True
            at.run()

        assert len(at.success) > 0

    def test_nav_buttons_present(self):
        """All navigation buttons must be present on the dashboard."""
        with patch("requests.get", return_value=_mock_healthy()):
            at = AppTest.from_file(str(APP_FILE))
            at.session_state["authenticated"] = True
            at.run()

        labels = {b.label for b in at.button}
        assert any("Protocol" in lbl for lbl in labels)
        assert any("Reagent" in lbl for lbl in labels)
        assert any("Experiment" in lbl for lbl in labels)

    def test_logout_button_present(self):
        """Sidebar logout button must be present when authenticated."""
        with patch("requests.get", return_value=_mock_healthy()):
            at = AppTest.from_file(str(APP_FILE))
            at.session_state["authenticated"] = True
            at.run()

        labels = [b.label for b in at.button]
        assert any("Logout" in lbl for lbl in labels)


# ---------------------------------------------------------------------------
# Authenticated — unhealthy backend
# ---------------------------------------------------------------------------

class TestBackendOffline:
    def test_connection_error_shows_error_message(self):
        """A connection error to the backend must show an error message."""
        with patch("requests.get", side_effect=req.exceptions.ConnectionError()):
            at = AppTest.from_file(str(APP_FILE))
            at.session_state["authenticated"] = True
            at.run()

        assert len(at.error) > 0

    def test_timeout_shows_error_message(self):
        """A backend timeout must show an error message."""
        with patch("requests.get", side_effect=req.exceptions.Timeout()):
            at = AppTest.from_file(str(APP_FILE))
            at.session_state["authenticated"] = True
            at.run()

        assert len(at.error) > 0

    def test_non_200_response_shows_error(self):
        """A non-200 health response must show an error message."""
        m = MagicMock()
        m.status_code = 503
        with patch("requests.get", return_value=m):
            at = AppTest.from_file(str(APP_FILE))
            at.session_state["authenticated"] = True
            at.run()

        assert len(at.error) > 0
