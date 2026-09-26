#!/bin/bash
echo "==================================================="
echo "🚀 Starting Developer Mode (Hot Reload Active)"
echo "==================================================="
echo ""

# Start backend in background and log to backend.log
echo "Starting Spring Boot backend (logging to backend.log)..."
export AWS_REGION=us-east-1
./mvnw spring-boot:run > backend.log 2>&1 &
BACKEND_PID=$!

# Start frontend in background and log to frontend.log
echo "Starting Vite frontend dev server (logging to frontend.log)..."
cd frontend
npm run dev > ../frontend.log 2>&1 &
FRONTEND_PID=$!
cd ..

# Handle shutdown
cleanup() {
    echo ""
    echo "Stopping dev servers (PIDs: $BACKEND_PID, $FRONTEND_PID)..."
    kill $BACKEND_PID 2>/dev/null
    kill $FRONTEND_PID 2>/dev/null
    exit
}
trap cleanup SIGINT SIGTERM

echo ""
echo "Dev servers started successfully in background."
echo "- Backend API:  http://localhost:8173"
echo "- Frontend App: http://localhost:5173"
echo ""
echo "📝 Log files created in project root:"
echo "  - To monitor Backend:  tail -f backend.log"
echo "  - To monitor Frontend: tail -f frontend.log"
echo ""
echo "Press Ctrl+C to stop both servers."
echo ""

# Wait for both
wait
