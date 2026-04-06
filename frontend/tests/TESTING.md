# Frontend Test Suite

Python/Streamlit test suite for the EliSmart LIMS frontend.

## Stack

| Role | Library |
|---|---|
| Test runner | **pytest** |
| Streamlit app runner | **`streamlit.testing.v1.AppTest`** |
| HTTP mocking | **`unittest.mock.patch`** (stdlib) |
| Assertions | plain `assert` (pytest style) |

This mirrors the Java backend stack: pytest ≈ JUnit, `AppTest` ≈ `@WebMvcTest` + MockMvc, `unittest.mock.patch` ≈ Mockito `@Mock`.

## Structure

```
frontend/
  tests/
    conftest.py              — env setup (BACKEND_URL, LOGIN_USER, LOGIN_PASS)
    test_app.py              — dashboard: auth gate, login form, backend health
    test_add_reagent.py      — reagent creation form: validation, success, errors
    test_add_protocol.py     — protocol creation form: validation, reagent binding
    test_add_experiment.py   — experiment creation: protocol selector, pair tables, submit
    test_search_experiments.py — search form, result rendering, pagination state
    test_experiment_details.py — read-only detail view, missing-ID guard
  pytest.ini                 — testpaths, output options
```

## Running the tests

From the `frontend/` directory:

```bash
# Install dependencies (if not already done)
pip install -r requirements.txt

# Run all tests
pytest

# Run a specific file
pytest tests/test_add_reagent.py

# Run a specific test class or function
pytest tests/test_add_reagent.py::TestBackendInteraction::test_valid_submission_shows_success

# Run with stdout output (useful for debugging)
pytest -s
```

## How it works

### AppTest

`AppTest.from_file("path/to/page.py")` loads the Streamlit script into a
sandboxed runner. Calling `.run()` executes it top-to-bottom, populating
widget collections (`at.text_input`, `at.button`, `at.selectbox`, etc.).

```python
at = AppTest.from_file(str(PAGE_FILE))
at.session_state["authenticated"] = True   # pre-seed state
at.run()                                   # first render
at.text_input[0].set_value("foo")          # interact
at.button[-1].click()                      # click submit
at.run()                                   # re-render with new state
assert len(at.success) > 0
```

### Auth gating

All pages call `_check_auth()`, which calls `st.stop()` when not authenticated.
Tests verify the auth gate by asserting that no form elements appear after
running without `authenticated=True` in `session_state`.

### HTTP mocking

Pages import `requests` at module level. All calls are patched with
`unittest.mock.patch("requests.get")` / `patch("requests.post")` applied as
context managers around the full `AppTest.from_file(...).run()` block.

```python
m = MagicMock()
m.status_code = 200
m.json.return_value = {"id": 1, "name": "IgG Test"}

with patch("requests.get", return_value=m):
    at = AppTest.from_file(str(PAGE_FILE))
    at.session_state["authenticated"] = True
    at.run()
```

For pages that call multiple different URLs, use `side_effect` with a routing
function:

```python
def _get_side_effect(url, **kwargs):
    m = MagicMock()
    m.status_code = 200
    if "protocols" in url:
        m.json.return_value = [...]
    elif "reagent-specs" in url:
        m.json.return_value = [...]
    return m

with patch("requests.get", side_effect=_get_side_effect):
    ...
```

### Environment variables

`conftest.py` sets the following env vars before any page module is evaluated:

| Variable | Test value | Purpose |
|---|---|---|
| `BACKEND_URL` | `http://testserver:8080` | Prevents pages from reading `secrets.toml` |
| `LOGIN_USER` | `admin` | Used by `app.py` to validate login |
| `LOGIN_PASS` | `secret` | Used by `app.py` to validate login |

## Widget access cheat sheet

| Widget | Access |
|---|---|
| `st.title` | `at.title[n].value` |
| `st.text_input` | `at.text_input[n].set_value("x")` |
| `st.number_input` | `at.number_input[n].set_value(1.0)` |
| `st.selectbox` | `at.selectbox[n].set_value("option")`, `.options` |
| `st.multiselect` | `at.multiselect[n].set_value(["a","b"])`, `.options` |
| `st.button` / `st.form_submit_button` | `at.button[n].click()`, `.label` |
| `st.success` | `at.success[n].value` |
| `st.error` | `at.error[n].value` |
| `st.warning` | `at.warning[n].value` |
| `st.info` | `at.info[n].value` |
| `st.metric` | `at.metric[n].value` |
| `st.dataframe` | `at.dataframe[n]` |
| `st.caption` | `at.caption[n].value` |
