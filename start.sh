#!/usr/bin/env bash
set -e

echo "🧪 Starting EliSmart LIMS..."

# Load environment variables
if [ -f .env ]; then
    set -a
    source .env
    set +a
    echo "✅ Environment loaded from .env"
fi

# Start backend
echo "🚀 Starting backend..."
./mvnw spring-boot:run -q &
BACKEND_PID=$!

# Wait for backend health
echo "⏳ Waiting for backend..."
for i in $(seq 1 30); do
    if curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
        echo "✅ Backend is ready"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "❌ Backend failed to start"
        kill $BACKEND_PID 2>/dev/null
        exit 1
    fi
    sleep 2
done

# Start frontend
echo "🚀 Starting frontend..."
cd frontend
streamlit run app.py &
FRONTEND_PID=$!
cd ..

echo ""
echo "✅ EliSmart is running!"
echo "   Frontend: http://localhost:8501"
echo "   Backend:  http://localhost:8080"
echo ""
echo "Press Ctrl+C to stop both services."

# Trap Ctrl+C to kill both
trap "echo '🛑 Shutting down...'; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit 0" INT TERM

wait
