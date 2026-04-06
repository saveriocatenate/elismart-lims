"""
Tests for pages/search_experiments.py — the experiment search and results page.

Covers:
- Auth gate
- Search form renders correctly
- Search returning results shows the result list (Details buttons appear)
- Search returning an empty list shows the empty-state info message
- Backend error on search surfaces to the user
"""
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
import requests as req
from streamlit.testing.v1 import AppTest

from helpers import click_button

PAGE_FILE = Path(__file__).parent.parent / "pages" / "search_experiments.py"
SUBMIT_LABEL = "Search"

SAMPLE_EXPERIMENT = {
    "id": 1,
    "name": "Run 2026-04-06",
    "date": "2026-04-06T10:00:00",
    "status": "OK",
    "protocolName": "IgG Test",
}

SEARCH_RESPONSE_WITH_RESULTS = {
    "content": [SAMPLE_EXPERIMENT],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "last": True,
}

SEARCH_RESPONSE_EMPTY = {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "last": True,
}


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
        """Without authentication the search form must not appear."""
        at = AppTest.from_file(str(PAGE_FILE))
        at.run()

        assert len(at.title) == 0

    def test_unauthenticated_does_not_render_inputs(self):
        """Text inputs must not be present when user is not authenticated."""
        at = AppTest.from_file(str(PAGE_FILE))
        at.run()

        assert len(at.text_input) == 0


# ---------------------------------------------------------------------------
# Rendering
# ---------------------------------------------------------------------------

class TestRendering:
    def test_shows_title(self):
        """Authenticated user sees the 'Search Experiments' title."""
        at = AppTest.from_file(str(PAGE_FILE))
        at.session_state["authenticated"] = True
        at.run()

        assert at.title[0].value == "Search Experiments"

    def test_search_form_inputs_present(self):
        """Name, date range, and status inputs must all be present."""
        at = AppTest.from_file(str(PAGE_FILE))
        at.session_state["authenticated"] = True
        at.run()

        assert len(at.text_input) >= 1
        assert len(at.selectbox) >= 1

    def test_status_selectbox_has_correct_options(self):
        """Status dropdown must include OK, KO, and VALIDATION_ERROR."""
        at = AppTest.from_file(str(PAGE_FILE))
        at.session_state["authenticated"] = True
        at.run()

        options = at.selectbox[0].options
        assert "OK" in options
        assert "KO" in options
        assert "VALIDATION_ERROR" in options


# ---------------------------------------------------------------------------
# Search results
# ---------------------------------------------------------------------------

class TestSearchResults:
    def test_search_with_results_shows_details_buttons(self):
        """After a search returning results each row must have a 'Details' button."""
        with patch("requests.post", return_value=_mock_post(200, SEARCH_RESPONSE_WITH_RESULTS)):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

            click_button(at, SUBMIT_LABEL)
            at.run()

        detail_buttons = [b for b in at.button if b.label and "Details" in b.label]
        assert len(detail_buttons) == 1

    def test_search_with_empty_results_shows_info(self):
        """An empty search result must show the no-experiments info message."""
        with patch("requests.post", return_value=_mock_post(200, SEARCH_RESPONSE_EMPTY)):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

            click_button(at, SUBMIT_LABEL)
            at.run()

        assert len(at.info) > 0
        assert "No experiments found" in at.info[0].value

    def test_search_backend_error_shows_error(self):
        """A non-200 search response must show an error message."""
        with patch("requests.post", return_value=_mock_post(500, {})):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

            click_button(at, SUBMIT_LABEL)
            at.run()

        assert len(at.error) > 0

    def test_search_connection_error_shows_error(self):
        """A network failure during search must show an error message."""
        with patch("requests.post", side_effect=req.exceptions.ConnectionError()):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.run()

            click_button(at, SUBMIT_LABEL)
            at.run()

        assert len(at.error) > 0

    def test_result_count_caption_shown(self):
        """The 'N total — page X of Y' caption must appear when there are results."""
        with patch("requests.post", return_value=_mock_post(200, SEARCH_RESPONSE_WITH_RESULTS)):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.session_state["exp_results"] = SEARCH_RESPONSE_WITH_RESULTS
            at.run()

        captions = [c.value for c in at.caption]
        assert any("total" in c for c in captions)
