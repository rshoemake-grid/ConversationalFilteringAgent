#!/usr/bin/env bash
ROOT="$(cd "$(dirname "$0")" && pwd)"

# Start backend in background
echo "Starting backend on http://localhost:8080..."
cd "$ROOT/backend"
# TLS/ALPN fix for gRPC (helps with "Failed ALPN negotiation" behind VPN/proxy)
export MAVEN_OPTS="-Djdk.tls.client.protocols=TLSv1.2,TLSv1.3 ${MAVEN_OPTS:-}"
./mvnw spring-boot:run -q -Dmaven.resources.skip=true &
BACKEND_PID=$!

# Wait for backend to be ready
echo "Waiting for backend to start..."
for i in $(seq 1 30); do
  if curl -s -o /dev/null -w "%{http_code}" -X POST -H "Content-Type: application/json" -H "Referer: http://localhost:5173/" -d '{"mode":"convo_commerce","message":"hi"}' http://localhost:8080/api/chat 2>/dev/null | grep -qE '^2[0-9]{2}$'; then
    echo "Backend ready."
    break
  fi
  sleep 2
  if [ "$i" -eq 30 ]; then
    echo "Backend failed to start in time."
    kill $BACKEND_PID 2>/dev/null
    exit 1
  fi
done

# Start frontend
echo "Starting frontend on http://localhost:5173..."
cd "$ROOT/frontend"
npm run dev &
FRONTEND_PID=$!

echo ""
echo "Application running:"
echo "  Backend:  http://localhost:8080"
echo "  Frontend: http://localhost:5173"
echo ""
echo "Press Ctrl+C to stop both."

cleanup() {
  echo ""
  echo "Stopping..."
  kill $BACKEND_PID $FRONTEND_PID 2>/dev/null
  exit 0
}
trap cleanup SIGINT SIGTERM

wait
