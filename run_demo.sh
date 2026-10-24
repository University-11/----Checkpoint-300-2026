#!/usr/bin/env bash
set -euo pipefail

echo "======================================================================"
echo " AXENIX JAVA BLUEPRINTS v3.0 — PRODUCTION PIPELINE & QUALITY GATE"
echo "======================================================================"
python3 server.py --verify
echo ""
echo "All 16 Test Suites + 50 Differential Fuzzing Seeds PASSED!"
echo "Starting Web IDE Server on http://localhost:${PORT:-8080} ..."
python3 server.py
