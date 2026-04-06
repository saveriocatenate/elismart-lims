"""
Tests for pages/experiment_details.py — the read-only experiment detail view.

Covers:
- Auth gate
- No experiment selected in session state shows a warning
- A valid experiment ID renders metadata, measurement pairs, and reagent batches
- Backend 404 shows an error
- Backend connection error shows an error
"""
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
import requests as req
from streamlit.testing.v1 import AppTest

PAGE_FILE = Path(__file__).parent.parent / "pages" / "experiment_details.py"

EXPERIMENT_DETAIL = {
    "id": 1,
    "name": "Run 2026-04-06",
    "date": "2026-04-06T10:00:00",
    "status": "OK",
    "protocolName": "IgG Test",
    "usedReagentBatches": [
        {"id": 1, "reagentName": "Anti-IgG", "lotNumber": "LOT-001", "expiryDate": "2027-01-01"}
    ],
    "measurementPairs": [
        {
            "id": 1,
            "pairType": "CALIBRATION",
            "concentrationNominal": 100.0,
            "signal1": 0.45,
            "signal2": 0.47,
            "signalMean": 0.46,
            "cvPct": 3.04,
            "recoveryPct": 98.5,
            "isOutlier": False,
        }
    ],
}


def _mock_get(status_code: int, body: dict):
    m = MagicMock()
    m.status_code = status_code
    m.json.return_value = body
    return m


# ---------------------------------------------------------------------------
# Auth gate
# ---------------------------------------------------------------------------

class TestAuthGate:
    def test_unauthenticated_does_not_render_content(self):
        """Without authentication the page must stop before rendering anything."""
        at = AppTest.from_file(str(PAGE_FILE))
        at.run()

        assert len(at.title) == 0

    def test_unauthenticated_no_metric_rendered(self):
        """Metrics must not appear when not authenticated."""
        at = AppTest.from_file(str(PAGE_FILE))
        at.run()

        assert len(at.metric) == 0


# ---------------------------------------------------------------------------
# Missing session state
# ---------------------------------------------------------------------------

class TestMissingExperimentId:
    def test_no_selected_id_shows_warning(self):
        """If no experiment is selected in session state a warning must appear."""
        at = AppTest.from_file(str(PAGE_FILE))
        at.session_state["authenticated"] = True
        # selected_exp_id intentionally not set
        at.run()

        assert len(at.warning) > 0

    def test_no_selected_id_does_not_show_title(self):
        """The 'Experiment Details' title must not appear without a selected experiment."""
        at = AppTest.from_file(str(PAGE_FILE))
        at.session_state["authenticated"] = True
        at.run()

        titles = [t.value for t in at.title]
        assert "Experiment Details" not in titles


# ---------------------------------------------------------------------------
# Happy path
# ---------------------------------------------------------------------------

class TestExperimentDetailView:
    def test_shows_experiment_details_title(self):
        """With a valid selected_exp_id the detail title must appear."""
        with patch("requests.get", return_value=_mock_get(200, EXPERIMENT_DETAIL)):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.session_state["selected_exp_id"] = 1
            at.run()

        assert at.title[0].value == "Experiment Details"

    def test_shows_status_metric(self):
        """The experiment status must be displayed as a metric."""
        with patch("requests.get", return_value=_mock_get(200, EXPERIMENT_DETAIL)):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.session_state["selected_exp_id"] = 1
            at.run()

        assert len(at.metric) > 0
        assert at.metric[0].value == "OK"

    def test_measurement_pairs_dataframe_shown(self):
        """A dataframe of measurement pairs must be rendered."""
        with patch("requests.get", return_value=_mock_get(200, EXPERIMENT_DETAIL)):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.session_state["selected_exp_id"] = 1
            at.run()

        assert len(at.dataframe) > 0

    def test_reagent_batches_dataframe_shown(self):
        """A dataframe of reagent batches must be rendered."""
        with patch("requests.get", return_value=_mock_get(200, EXPERIMENT_DETAIL)):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.session_state["selected_exp_id"] = 1
            at.run()

        assert len(at.dataframe) >= 2

    def test_empty_pairs_shows_info(self):
        """No measurement pairs must show the empty-state info message."""
        detail_no_pairs = {**EXPERIMENT_DETAIL, "measurementPairs": []}
        with patch("requests.get", return_value=_mock_get(200, detail_no_pairs)):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.session_state["selected_exp_id"] = 1
            at.run()

        info_texts = [i.value for i in at.info]
        assert any("measurement pairs" in t.lower() for t in info_texts)

    def test_empty_batches_shows_info(self):
        """No reagent batches must show the empty-state info message."""
        detail_no_batches = {**EXPERIMENT_DETAIL, "usedReagentBatches": []}
        with patch("requests.get", return_value=_mock_get(200, detail_no_batches)):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.session_state["selected_exp_id"] = 1
            at.run()

        info_texts = [i.value for i in at.info]
        assert any("reagent" in t.lower() for t in info_texts)


# ---------------------------------------------------------------------------
# Backend errors
# ---------------------------------------------------------------------------

class TestBackendErrors:
    def test_backend_404_shows_error(self):
        """A 404 response for the experiment must show an error message."""
        with patch("requests.get", return_value=_mock_get(404, {})):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.session_state["selected_exp_id"] = 999
            at.run()

        assert len(at.error) > 0

    def test_connection_error_shows_error(self):
        """A network failure loading experiment details must show an error."""
        with patch("requests.get", side_effect=req.exceptions.ConnectionError()):
            at = AppTest.from_file(str(PAGE_FILE))
            at.session_state["authenticated"] = True
            at.session_state["selected_exp_id"] = 1
            at.run()

        assert len(at.error) > 0
