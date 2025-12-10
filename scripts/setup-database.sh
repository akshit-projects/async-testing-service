#!/bin/bash

# Database setup script for Async Testing Service
# This script creates the database and user for the application

set -e

# Configuration
DB_NAME="asynctester"
DB_USER="asynctester"
DB_PASSWORD="asynctester"
DB_HOST="localhost"
DB_PORT="5432"

echo "Setting up PostgreSQL database for Async Testing Service..."

# Check if PostgreSQL is running
if ! pg_isready -h $DB_HOST -p $DB_PORT > /dev/null 2>&1; then
    echo "Error: PostgreSQL is not running on $DB_HOST:$DB_PORT"
    echo "Please start PostgreSQL and try again."
    exit 1
fi

echo "PostgreSQL is running. Proceeding with database setup..."

# Create database and user (run as postgres user)
psql -h $DB_HOST -p $DB_PORT -U postgres -c "
-- Create user if not exists
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$DB_USER') THEN
        CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD';
    END IF;
END
\$\$;

-- Create database if not exists
SELECT 'CREATE DATABASE $DB_NAME OWNER $DB_USER'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$DB_NAME')\gexec

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;
"

echo "Database and user created successfully."

# Test connection
echo "Testing database connection..."
if PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT version();" > /dev/null 2>&1; then
    echo "✅ Database connection successful!"
    echo ""
    echo "Database setup complete:"
    echo "  Host: $DB_HOST:$DB_PORT"
    echo "  Database: $DB_NAME"
    echo "  User: $DB_USER"
    echo "  Password: $DB_PASSWORD"
    echo ""
    echo "You can now run the application with: sbt run"
    echo "Flyway migrations will be applied automatically on startup."
else
    echo "❌ Database connection failed!"
    exit 1
fi
