#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

LOG_FILE="$SCRIPT_DIR/app.log"

echo "==================================================="
echo "🛡️ Intelligent Compliance & Audit Agent Launcher"
echo "==================================================="
echo ""

echo "[1/2] Compiling ReactJS frontend..."
(cd frontend && npm run build)

echo ""
echo "[2/2] Launching Spring Boot server in detached mode (port 8173)..."
echo "Logging output to: $LOG_FILE"

./mvnw spring-boot:run > "$LOG_FILE" 2>&1 &
SERVER_PID=$!
cd ..

# Handle shutdown
cleanup() {
    echo ""
    echo "Stopping server (PID: $SERVER_PID)..."
    kill $SERVER_PID 2>/dev/null
    exit
}
trap cleanup SIGINT SIGTERM

echo "✅ AuditAgent started in background with PID $SERVER_PID"
echo "👉 To view live logs: tail -f app.log"
#echo "👉 To stop server:    fuser -k 8173/tcp (or kill $SERVER_PID)"
echo ""
echo "Press Ctrl+C to stop the server."
echo ""

# Wait for the server process to finish
wait
