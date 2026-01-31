#!/bin/bash

# Function to handle termination signals
handle_term() {
    echo "Termination signal received. Shutting down..."
    kill -TERM "$PLAY_PID" 2>/dev/null
    kill -TERM "$WORKER_PID" 2>/dev/null
    wait "$PLAY_PID"
    wait "$WORKER_PID"
    echo "Shutdown complete."
    exit 0
}

trap handle_term SIGTERM SIGINT

echo "Starting Async Testing Service..."

# Check if we should run the Play server
if [[ "${RUN_SERVER}" != "false" ]]; then
    echo "Starting Play server..."
    /app/play/bin/asynctester -Dplay.http.secret.key=${PLAY_SECRET:-changeme} &
    PLAY_PID=$!
fi

# Check if we should run the workers
if [[ "${RUN_WORKER}" != "false" ]]; then
    echo "Starting Worker application..."
    /app/worker/bin/asynctesterworkers &
    WORKER_PID=$!
fi

# Wait for processes
if [[ -n "$PLAY_PID" ]] && [[ -n "$WORKER_PID" ]]; then
    wait -n "$PLAY_PID" "$WORKER_PID"
elif [[ -n "$PLAY_PID" ]]; then
    wait "$PLAY_PID"
elif [[ -n "$WORKER_PID" ]]; then
    wait "$WORKER_PID"
else
    echo "No processes started. check RUN_SERVER and RUN_WORKER env variables."
    exit 1
fi

# If any process exits, kill the other one and exit
handle_term
