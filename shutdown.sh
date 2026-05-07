#!/usr/bin/env bash
echo "Shutting down Conversational Filtering Agent..."
echo

echo "Stopping backend (port 8080)..."
if command -v lsof >/dev/null 2>&1; then
  for pid in $(lsof -ti :8080 2>/dev/null); do
    kill "$pid" 2>/dev/null && echo "  Backend stopped (PID $pid)."
  done
elif command -v fuser >/dev/null 2>&1; then
  fuser -k 8080/tcp 2>/dev/null && echo "  Backend stopped."
fi

echo "Stopping frontend (port 5173)..."
if command -v lsof >/dev/null 2>&1; then
  for pid in $(lsof -ti :5173 2>/dev/null); do
    kill "$pid" 2>/dev/null && echo "  Frontend stopped (PID $pid)."
  done
elif command -v fuser >/dev/null 2>&1; then
  fuser -k 5173/tcp 2>/dev/null && echo "  Frontend stopped."
fi

echo
echo "Done."
