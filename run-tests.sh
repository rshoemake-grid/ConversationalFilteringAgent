#!/usr/bin/env bash
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"

echo "=== Running Backend Tests ==="
cd "$ROOT/backend"
./mvnw test -q
echo "Backend tests passed."
echo

echo "=== Running Frontend Tests ==="
cd "$ROOT/frontend"
npm run test -- --run
echo "Frontend tests passed."
echo

echo "=== All tests passed ==="
