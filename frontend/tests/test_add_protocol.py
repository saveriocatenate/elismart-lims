"""
Tests for pages/add_protocol.py — the protocol creation form.

Covers:
- Auth gate
- Form renders correctly (including reagent multiselect when catalog is populated)
- Validation: name is required
- Happy path: protocol + reagent specs created successfully
- Backend error on protocol creation is surfaced
"""
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
import requests as req
from streamlit.testing.v1 import AppTest

from helpers import click_button

PAGE_FILE = Path(__file__).parent.parent / "pages" / "add_protocol.py"
SUBMIT_LABEL = "Create Protocol"

REAGENT_CATALOG_PAGE = {
    "content": [
        {"id": 1, "name": "Anti-IgG", "manufacturer": "Sigma"},
        {"id": 2, "name": "HRP Conjugate", "manufacturer": "Merck"},
    ]
}

PROTOCOL_RESPONSE = {
    "id": 10,
    "name": "IgG Test",
    "numCalibrationPairs": 7,
    "numControlPairs": 3,
    "maxCvAllowed": 15.0,
    "maxErrorAllowed": 10.0,
}

SPEC_RESPONSE = {"id": 1, "protocolId": 10, "reagentId": 1, "reagentName": "Anti-IgG", "isMandatory": True}


def _get_side_effect(url, **kwargs):
    m = MagicMock()
    m.status_code = 200
    if "reagent-catalogs" in url:
        m.json.return_value = REAGENT_CATALOG_PAGE
    else:
        m.json.return_value = {}
    return m


def _post_side_effect(url, **kwargs):
    m = MagicMock()
    m.status_code = 201
    if "protocol-reagent-specs" in url:
        m.json.return_value = SPEC_RESPONSE
    else:
        m.json.return_value = PROTOCOL_RESPONSE
    return m


# ---------------------------------------------------------------------------
# Auth gate
# ---------------------------------------------------------------------------

class TestAuthGate:
    def test_unauthenticated_does_not_render_form(self):
        """Without authentication the page must stop before rendering the form."""
        with patch("requests.get", side_effect=_get_side_effect):
            at = AppTest.from_file(str(PAGE_FILE))
            at.run()

        assert len(at.title) == 0

    def test_unauthenticated_does_not_render_inputs(self):
        """Form inputs must not appear when the user is not authenticated."""
        with patch("requests.get", side_effect=_get_side_effect):
            at = AppTest.from_file(str(PAGE_FILE))
            at.run()

        assert len(at.text_input) == 0


# ---------------------------------------------------------------------------
# Rendering
# ---------------------------------------------------------------------------

class TestRendering:
    def test_shows_title(self):
        """Authenticated user sees 'New Protocol' title."""
        with patch("requests.get", side_effect=_get_side_effect):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

        assert at.title[0].value == "New Protocol"

    def test_name_input_present(self):
        """A text input for the protocol name must be present."""
        with patch("requests.get", side_effect=_get_side_effect):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

        assert len(at.text_input) >= 1

    def test_numeric_pair_inputs_present(self):
        """Calibration pairs, control pairs, CV, and error fields must render."""
        with patch("requests.get", side_effect=_get_side_effect):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

        assert len(at.number_input) >= 4

    def test_reagent_multiselect_populated(self):
        """The reagent multiselect must list options from the catalog."""
        with patch("requests.get", side_effect=_get_side_effect):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

        assert len(at.multiselect) > 0
        options = at.multiselect[0].options
        assert any("Anti-IgG" in opt for opt in options)


# ---------------------------------------------------------------------------
# Form validation
# ---------------------------------------------------------------------------

class TestValidation:
    def test_empty_name_shows_error(self):
        """Submitting without a protocol name must show an error."""
        with patch("requests.get", side_effect=_get_side_effect):
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
        """A complete, valid form submission must show a success message."""
        with patch("requests.get", side_effect=_get_side_effect), \
             patch("requests.post", side_effect=_post_side_effect):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

            at.text_input[0].set_value("IgG Test Protocol")
            click_button(at, SUBMIT_LABEL)
            at.run()

        assert len(at.success) > 0

    def test_backend_error_on_protocol_create_shows_error(self):
        """An API error when creating the protocol must surface to the user."""
        m_err = MagicMock()
        m_err.status_code = 500
        m_err.json.return_value = {"detail": "Server error"}
        m_err.text = "Server error"

        with patch("requests.get", side_effect=_get_side_effect), \
             patch("requests.post", return_value=m_err):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

            at.text_input[0].set_value("IgG Test Protocol")
            click_button(at, SUBMIT_LABEL)
            at.run()

        assert len(at.error) > 0

    def test_connection_error_on_submit_shows_error(self):
        """A network failure during protocol creation must show an error."""
        with patch("requests.get", side_effect=_get_side_effect), \
             patch("requests.post", side_effect=req.exceptions.ConnectionError()):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

            at.text_input[0].set_value("IgG Test Protocol")
            click_button(at, SUBMIT_LABEL)
            at.run()

        assert len(at.error) > 0
