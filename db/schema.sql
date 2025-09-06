-- PostgreSQL Schema for Async Testing Service
-- Generated from domain models: Floww, Execution, FlowVersion

-- Create database (run separately if needed)
-- CREATE DATABASE asynctester;
-- CREATE USER asynctester WITH PASSWORD 'asynctester';
-- GRANT ALL PRIVILEGES ON DATABASE asynctester TO asynctester;

-- Connect to asynctester database before running the rest

-- Enable UUID extension for generating UUIDs
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =====================================================
-- USERS TABLE
-- Based on: domain/user/User.scala
-- =====================================================
CREATE TABLE users (
    id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    profile_picture TEXT,
    phone_number VARCHAR(50),
    company VARCHAR(255),
    role VARCHAR(50) NOT NULL DEFAULT 'user' CHECK (role IN ('user', 'admin')),
    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
    user_updated_fields TEXT, -- Array of field names that user has manually updated
    last_google_sync BIGINT, -- Timestamp of last Google data sync
    created_at BIGINT NOT NULL,
    modified_at BIGINT NOT NULL
);

-- Indexes for users table
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_is_admin ON users(is_admin);

-- =====================================================
-- FLOWS TABLE
-- Based on: domain/flow/Floww.scala
-- =====================================================
CREATE TABLE flows (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    creator VARCHAR(255) NOT NULL,
    steps text NOT NULL,
    created_at BIGINT NOT NULL,
    modified_at BIGINT NOT NULL,
    flow_version INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE flow_versions (
    id VARCHAR(255) PRIMARY KEY,
    flow_id VARCHAR(255) NOT NULL,
    version INTEGER NOT NULL,
    steps text NOT NULL,
    created_at BIGINT NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    description TEXT,
    
    -- Foreign key constraint
    CONSTRAINT fk_flow_versions_flow_id FOREIGN KEY (flow_id) REFERENCES flows(id) ON DELETE CASCADE,
    
    -- Unique constraint for flow_id + version combination
    CONSTRAINT flow_versions_flow_version_unique UNIQUE (flow_id, version)
);
CREATE INDEX idx_flow_versions_flow_id ON flow_versions(flow_id);

CREATE TABLE executions (
    id VARCHAR(255) PRIMARY KEY,
    flowId VARCHAR(255) NOT NULL,
    flowVersion INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('TODO', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    startedAt TIMESTAMP WITH TIME ZONE NOT NULL, -- Instant type
    completedAt TIMESTAMP WITH TIME ZONE, -- Optional Instant
    steps JSONB NOT NULL,
    updatedAt TIMESTAMP WITH TIME ZONE NOT NULL, -- Instant type
    parameters JSONB,
    testSuiteExecutionId VARCHAR(255), -- Optional reference to test suite execution

    -- Foreign key constraint to flows table
    CONSTRAINT fk_executions_flow_id FOREIGN KEY (flowId) REFERENCES flows(id) ON DELETE CASCADE
);

-- Indexes for executions table
CREATE INDEX idx_executions_flow_id ON executions(flowId);
CREATE INDEX idx_executions_test_suite_execution_id ON executions(testSuiteExecutionId);

-- =====================================================
-- TEST SUITES TABLE
-- Based on: domain/testsuite/TestSuite.scala
-- =====================================================
CREATE TABLE test_suites (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    creator VARCHAR(255) NOT NULL,
    flows JSONB NOT NULL, -- List of TestSuiteFlowConfig
    runUnordered BOOLEAN NOT NULL DEFAULT FALSE,
    createdAt BIGINT NOT NULL,
    modifiedAt BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_test_suites_creator ON test_suites(creator);
CREATE INDEX idx_test_suites_enabled ON test_suites(enabled);

-- =====================================================
-- TEST SUITE EXECUTIONS TABLE
-- Based on: domain/testsuite/TestSuiteExecution.scala
-- =====================================================
CREATE TABLE test_suite_executions (
    id VARCHAR(255) PRIMARY KEY,
    testSuiteId VARCHAR(255) NOT NULL,
    testSuiteName VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('TODO', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    startedAt TIMESTAMP WITH TIME ZONE NOT NULL,
    completedAt TIMESTAMP WITH TIME ZONE,
    flowExecutions JSONB NOT NULL, -- List of TestSuiteFlowExecution
    runUnordered BOOLEAN NOT NULL DEFAULT FALSE,
    triggeredBy VARCHAR(255) NOT NULL,
    totalFlows INTEGER NOT NULL,
    completedFlows INTEGER NOT NULL DEFAULT 0,
    failedFlows INTEGER NOT NULL DEFAULT 0,

    -- Foreign key constraint to test_suites table
    CONSTRAINT fk_test_suite_executions_test_suite_id FOREIGN KEY (testSuiteId) REFERENCES test_suites(id) ON DELETE CASCADE
);

CREATE INDEX idx_test_suite_executions_test_suite_id ON test_suite_executions(testSuiteId);
CREATE INDEX idx_test_suite_executions_triggered_by ON test_suite_executions(triggeredBy);
CREATE INDEX idx_test_suite_executions_status ON test_suite_executions(status);

-- Add foreign key constraint from executions to test_suite_executions (optional reference)
ALTER TABLE executions ADD CONSTRAINT fk_executions_test_suite_execution_id
    FOREIGN KEY (testSuiteExecutionId) REFERENCES test_suite_executions(id) ON DELETE SET NULL;

CREATE TABLE resource_configs (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    "group" VARCHAR(255),
    namespace VARCHAR(255),
    payload_json JSONB NOT NULL
);

CREATE INDEX idx_resource_configs_group ON resource_configs("group");
