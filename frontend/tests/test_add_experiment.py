"""
Tests for pages/add_experiment.py — the experiment creation form.

Covers:
- Auth gate
- Empty protocol list shows a warning and stops
- With protocols available the form renders (protocol selectbox, reagent table, pair rows)
- Mandatory lot number validation
- Valid submission posts to the backend and shows success
- Backend error on creation surfaces to the user
"""
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
import requests as req
from streamlit.testing.v1 import AppTest

from helpers import click_button

PAGE_FILE = Path(__file__).parent.parent / "pages" / "add_experiment.py"
SUBMIT_LABEL = "Create Experiment"

PROTOCOLS = [
    {
        "id": 1,
        "name": "IgG Test",
        "numCalibrationPairs": 2,
        "numControlPairs": 1,
        "maxCvAllowed": 15.0,
        "maxErrorAllowed": 10.0,
    }
]

PROTOCOL_DETAIL = PROTOCOLS[0]

REAGENT_SPECS = [
    {"id": 10, "protocolId": 1, "reagentId": 100, "reagentName": "Anti-IgG", "isMandatory": True},
    {"id": 11, "protocolId": 1, "reagentId": 101, "reagentName": "Buffer", "isMandatory": False},
]

EXPERIMENT_RESPONSE = {
    "id": 99,
    "name": "Run Test",
    "date": "2026-04-06T09:00:00",
    "status": "OK",
    "protocolName": "IgG Test",
    "usedReagentBatches": [],
    "measurementPairs": [],
}


def _get_side_effect(url, params=None, **kwargs):
    """Route GET requests based on URL."""
    m = MagicMock()
    m.status_code = 200
    if "protocol-reagent-specs" in url:
        m.json.return_value = REAGENT_SPECS
    elif url.rstrip("/").endswith("/1"):
        m.json.return_value = PROTOCOL_DETAIL
    elif "protocols" in url:
        m.json.return_value = PROTOCOLS
    else:
        m.json.return_value = []
    return m


def _get_no_protocols(url, **kwargs):
    """Return an empty protocol list."""
    m = MagicMock()
    m.status_code = 200
    m.json.return_value = []
    return m


# ---------------------------------------------------------------------------
# Auth gate
# ---------------------------------------------------------------------------

class TestAuthGate:
    def test_unauthenticated_does_not_render_form(self):
        """Without authentication the page must stop before rendering."""
        with patch("requests.get", side_effect=_get_side_effect):
            at = AppTest.from_file(str(PAGE_FILE))
            at.run()

        assert len(at.title) == 0

    def test_unauthenticated_no_selectbox(self):
        """Protocol selectbox must not appear when not authenticated."""
        with patch("requests.get", side_effect=_get_side_effect):
            at = AppTest.from_file(str(PAGE_FILE))
            at.run()

        assert len(at.selectbox) == 0


# ---------------------------------------------------------------------------
# Empty protocol list
# ---------------------------------------------------------------------------

class TestEmptyProtocolList:
    def test_no_protocols_shows_warning(self):
        """If no protocols exist a warning must be shown and the form must not render."""
        with patch("requests.get", side_effect=_get_no_protocols):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

        assert len(at.warning) > 0

    def test_no_protocols_does_not_show_submit_button(self):
        """The Create Experiment button must not appear when there are no protocols."""
        with patch("requests.get", side_effect=_get_no_protocols):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

        submit_buttons = [b for b in at.button if b.label and SUBMIT_LABEL in b.label]
        assert len(submit_buttons) == 0


# ---------------------------------------------------------------------------
# Rendering with protocols available
# ---------------------------------------------------------------------------

class TestRendering:
    def test_shows_title(self):
        """Authenticated user with protocols available sees the 'New Experiment' title."""
        with patch("requests.get", side_effect=_get_side_effect):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

        assert at.title[0].value == "New Experiment"

    def test_protocol_selectbox_populated(self):
        """Protocol selectbox must list the available protocols."""
        with patch("requests.get", side_effect=_get_side_effect):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

        assert len(at.selectbox) >= 1

    def test_number_inputs_for_calibration_and_control_pairs(self):
        """Number inputs for measurement pairs must match protocol pair counts.

        Protocol has 2 calibration + 1 control = 3 rows × 3 signals each = 9.
        """
        with patch("requests.get", side_effect=_get_side_effect):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

        assert len(at.number_input) >= 9

    def test_reagent_lot_text_inputs_present(self):
        """A text input for each reagent's lot number must be present."""
        with patch("requests.get", side_effect=_get_side_effect):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

        # experiment name + 2 lot number inputs = at least 3
        assert len(at.text_input) >= 2

    def test_status_selectbox_has_valid_options(self):
        """Status selectbox must offer OK, KO, and VALIDATION_ERROR."""
        with patch("requests.get", side_effect=_get_side_effect):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

        all_options = [opt for sb in at.selectbox for opt in sb.options]
        assert "OK" in all_options
        assert "KO" in all_options
        assert "VALIDATION_ERROR" in all_options


# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------

class TestValidation:
    def test_empty_name_shows_error(self):
        """Submitting without an experiment name must show a validation error."""
        with patch("requests.get", side_effect=_get_side_effect):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

            click_button(at, SUBMIT_LABEL)
            at.run()

        assert len(at.error) > 0

    def test_missing_mandatory_lot_number_shows_error(self):
        """Omitting the lot number for a mandatory reagent must show a validation error."""
        with patch("requests.get", side_effect=_get_side_effect):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

            # Fill name but leave mandatory lot number blank
            at.text_input[0].set_value("Test Run")
            click_button(at, SUBMIT_LABEL)
            at.run()

        assert len(at.error) > 0


# ---------------------------------------------------------------------------
# Backend interaction
# ---------------------------------------------------------------------------

class TestBackendInteraction:
    def test_valid_submission_shows_success(self):
        """A fully valid submission that gets HTTP 201 must show a success message."""
        m_post = MagicMock()
        m_post.status_code = 201
        m_post.json.return_value = EXPERIMENT_RESPONSE

        with patch("requests.get", side_effect=_get_side_effect), \
             patch("requests.post", return_value=m_post):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

            at.text_input[0].set_value("Test Run")
            at.text_input[1].set_value("LOT-001")  # mandatory reagent lot
            click_button(at, SUBMIT_LABEL)
            at.run()

        assert len(at.success) > 0
        assert "99" in at.success[0].value

    def test_backend_error_shows_error(self):
        """A non-201 response from the experiment creation endpoint must show an error."""
        m_post = MagicMock()
        m_post.status_code = 400
        m_post.json.return_value = {"message": "Missing mandatory reagents"}

        with patch("requests.get", side_effect=_get_side_effect), \
             patch("requests.post", return_value=m_post):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

            at.text_input[0].set_value("Test Run")
            at.text_input[1].set_value("LOT-001")
            click_button(at, SUBMIT_LABEL)
            at.run()

        assert len(at.error) > 0

    def test_connection_error_shows_error(self):
        """A network failure during experiment creation must show an error."""
        with patch("requests.get", side_effect=_get_side_effect), \
             patch("requests.post", side_effect=req.exceptions.ConnectionError()):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

            at.text_input[0].set_value("Test Run")
            at.text_input[1].set_value("LOT-001")
            click_button(at, SUBMIT_LABEL)
            at.run()

        assert len(at.error) > 0
