#!/bin/bash

# Start Worker Script for Async Testing Service
# This script starts the worker application with proper logging

set -e

echo "Starting Async Testing Service Worker..."

# Set environment variables if needed
export KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-"localhost:9092"}
export WORKER_QUEUE_TOPIC=${WORKER_QUEUE_TOPIC:-"flow-executions"}
export REDIS_HOST=${REDIS_HOST:-"localhost"}
export REDIS_PORT=${REDIS_PORT:-"6379"}
export DATABASE_URL=${DATABASE_URL:-"jdbc:postgresql://localhost:5432/asynctester"}
export DATABASE_USER=${DATABASE_USER:-"asynctester"}
export DATABASE_PASSWORD=${DATABASE_PASSWORD:-"asynctester"}

echo "Configuration:"
echo "  Kafka Bootstrap Servers: $KAFKA_BOOTSTRAP_SERVERS"
echo "  Worker Queue Topic: $WORKER_QUEUE_TOPIC"
echo "  Redis Host: $REDIS_HOST:$REDIS_PORT"
echo "  Database URL: $DATABASE_URL"

# Create logs directory if it doesn't exist
mkdir -p logs

# Change to project directory
cd "$(dirname "$0")/.."

# Run the worker
echo "Starting worker application..."
sbt "workers/runMain ab.async.tester.workers.app.WorkerMain"
