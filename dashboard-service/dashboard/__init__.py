"""Use the recommendation service's calculations and security without copying them."""
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent
# An override supports an isolated checkout or deployment layout.
SHARED = Path(os.getenv("RECOMMENDATION_SERVICE_PATH", str(ROOT.parent / "Recommendation-service"))).resolve()
if not (SHARED / "recommendation" / "metrics.py").is_file():
    raise RuntimeError("Recommendation-service not found. Set RECOMMENDATION_SERVICE_PATH to its folder.")
sys.path.insert(0, str(SHARED))
