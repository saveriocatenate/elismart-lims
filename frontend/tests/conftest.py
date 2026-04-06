"""
Shared pytest configuration for the EliSmart LIMS frontend test suite.

Sets environment variables before any Streamlit page is imported, so that
module-level code (BACKEND_URL, LOGIN_USER/PASS resolution) uses test values
instead of reading secrets.toml or production env vars.
"""
import os
import sys
from pathlib import Path

# Add tests/ directory to sys.path so test modules can do `from helpers import ...`
sys.path.insert(0, str(Path(__file__).parent))

# Must be set before any page module is evaluated by AppTest
os.environ["BACKEND_URL"] = "http://testserver:8080"
os.environ["LOGIN_USER"] = "admin"
os.environ["LOGIN_PASS"] = "secret"
