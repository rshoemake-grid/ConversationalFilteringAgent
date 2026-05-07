#!/usr/bin/env bash
# List Gemini models via the app's GET /api/models endpoint.
# Requires backend running and GOOGLE_API_KEY (or app.gemini.api-key) set.
# Uses Referer header to satisfy API keys with website restrictions.
set -e
BASE="${1:-http://localhost:8080}"
REFERER="${2:-http://localhost:5173/}"
curl -s -H "Referer: $REFERER" "$BASE/api/models" | python3 -m json.tool
