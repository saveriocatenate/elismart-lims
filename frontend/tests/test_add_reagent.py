"""
Tests for pages/add_reagent.py — the reagent catalog creation form.

Covers:
- Unauthenticated state stops rendering (auth gate)
- Authenticated state renders the form
- Empty name or manufacturer shows a validation error
- Valid submission calls the backend and shows success
- Backend error is surfaced to the user
"""
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
import requests as req
from streamlit.testing.v1 import AppTest

from helpers import click_button

PAGE_FILE = Path(__file__).parent.parent / "pages" / "add_reagent.py"
SUBMIT_LABEL = "Add Reagent"


def _mock_post(status_code: int, body: dict):
    m = MagicMock()
    m.status_code = status_code
    m.json.return_value = body
    return m


# ---------------------------------------------------------------------------
# Auth gate
# ---------------------------------------------------------------------------

class TestAuthGate:
    def test_unauthenticated_does_not_render_form(self):
        """Without authentication the page must stop before rendering the form."""
        at = AppTest.from_file(str(PAGE_FILE))
        at.run()

        assert len(at.text_input) == 0

    def test_unauthenticated_does_not_render_title(self):
        """Without authentication the page title must not appear."""
        at = AppTest.from_file(str(PAGE_FILE))
        at.run()

        assert len(at.title) == 0


# ---------------------------------------------------------------------------
# Rendering
# ---------------------------------------------------------------------------

class TestRendering:
    def test_authenticated_shows_title(self):
        """Authenticated user sees the 'New Reagent' title."""
        at = AppTest.from_file(str(PAGE_FILE))
        at.session_state["authenticated"] = True
        at.run()

        assert at.title[0].value == "New Reagent"

    def test_authenticated_shows_name_and_manufacturer_inputs(self):
        """Name and Manufacturer text inputs must be rendered."""
        at = AppTest.from_file(str(PAGE_FILE))
        at.session_state["authenticated"] = True
        at.run()

        assert len(at.text_input) >= 2

    def test_logout_button_present(self):
        """Sidebar logout button must be visible."""
        at = AppTest.from_file(str(PAGE_FILE))
        at.session_state["authenticated"] = True
        at.run()

        labels = [b.label for b in at.button]
        assert any("Logout" in lbl for lbl in labels)


# ---------------------------------------------------------------------------
# Form validation
# ---------------------------------------------------------------------------

class TestValidation:
    def test_empty_name_shows_error(self):
        """Submitting with a blank name must show a validation error."""
        at = AppTest.from_file(str(PAGE_FILE))
        at.session_state["authenticated"] = True
        at.run()

        at.text_input[1].set_value("Sigma-Aldrich")  # manufacturer only
        click_button(at, SUBMIT_LABEL)
        at.run()

        assert len(at.error) > 0

    def test_empty_manufacturer_shows_error(self):
        """Submitting with a blank manufacturer must show a validation error."""
        at = AppTest.from_file(str(PAGE_FILE))
        at.session_state["authenticated"] = True
        at.run()

        at.text_input[0].set_value("Anti-IgG")  # name only
        click_button(at, SUBMIT_LABEL)
        at.run()

        assert len(at.error) > 0

    def test_both_fields_empty_shows_error(self):
        """Submitting an empty form must show a validation error."""
        at = AppTest.from_file(str(PAGE_FILE))
        at.session_state["authenticated"] = True
        at.run()

        click_button(at, SUBMIT_LABEL)
        at.run()

        assert len(at.error) > 0


# ---------------------------------------------------------------------------
# Backend interaction
# ---------------------------------------------------------------------------

class TestBackendInteraction:
    def test_valid_submission_shows_success(self):
        """A valid form submission that gets HTTP 201 back shows a success message."""
        with patch("requests.post", return_value=_mock_post(201, {"id": 42})):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

            at.text_input[0].set_value("Anti-IgG Antibody")
            at.text_input[1].set_value("Sigma-Aldrich")
            click_button(at, SUBMIT_LABEL)
            at.run()

        assert len(at.success) > 0
        assert "42" in at.success[0].value

    def test_backend_error_shows_error_message(self):
        """A non-201 backend response must surface an error to the user."""
        with patch("requests.post", return_value=_mock_post(500, {"detail": "Internal error"})):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

            at.text_input[0].set_value("Anti-IgG Antibody")
            at.text_input[1].set_value("Sigma-Aldrich")
            click_button(at, SUBMIT_LABEL)
            at.run()

        assert len(at.error) > 0

    def test_connection_error_shows_error_message(self):
        """A network failure on submit must show an error message."""
        with patch("requests.post", side_effect=req.exceptions.ConnectionError()):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

            at.text_input[0].set_value("Anti-IgG Antibody")
            at.text_input[1].set_value("Sigma-Aldrich")
            click_button(at, SUBMIT_LABEL)
            at.run()

        assert len(at.error) > 0
