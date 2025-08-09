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
    
    -- Foreign key constraint to flows table
    CONSTRAINT fk_executions_flow_id FOREIGN KEY (flowId) REFERENCES flows(id) ON DELETE CASCADE
);

-- Indexes for executions table
CREATE INDEX idx_executions_flow_id ON executions(flowId);

CREATE TABLE resource_configs (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    "group" VARCHAR(255),
    namespace VARCHAR(255),
    payload_json JSONB NOT NULL
);

CREATE INDEX idx_resource_configs_group ON resource_configs("group");
