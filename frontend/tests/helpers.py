"""Shared test utilities for the EliSmart LIMS frontend test suite."""
from streamlit.testing.v1 import AppTest


def click_button(at: AppTest, label_contains: str) -> None:
    """Find and click the first button whose label contains *label_contains*.

    Using label-based lookup avoids fragile index-based selection, which breaks
    when sidebar buttons (e.g. Logout) appear at the end of the button list and
    `at.button[-1]` would click Logout instead of the intended submit button.
    """
    for btn in at.button:
        if btn.label and label_contains in btn.label:
            btn.click()
            return
    raise ValueError(
        f"No button found with label containing '{label_contains}'. "
        f"Available: {[b.label for b in at.button]}"
    )
