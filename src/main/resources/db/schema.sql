-- =====================================================
-- COMPLETE DATABASE SCHEMA FOR ASYNC TESTING SERVICE
-- =====================================================

-- Drop existing tables if they exist (for clean setup)
DROP TABLE IF EXISTS test_suite_executions CASCADE;
DROP TABLE IF EXISTS test_suites CASCADE;
DROP TABLE IF EXISTS executions CASCADE;
DROP TABLE IF EXISTS flows CASCADE;
DROP TABLE IF EXISTS flow_versions CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS teams CASCADE;
DROP TABLE IF EXISTS organisations CASCADE;
DROP TABLE IF EXISTS resources CASCADE;

-- =====================================================
-- ORGANISATIONS TABLE
-- =====================================================
CREATE TABLE organisations (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at BIGINT NOT NULL,
    modified_at BIGINT NOT NULL
);

-- Indexes for organisations table
CREATE INDEX idx_organisations_name ON organisations(name);
CREATE INDEX idx_organisations_created_at ON organisations(created_at);

-- =====================================================
-- TEAMS TABLE
-- =====================================================
CREATE TABLE teams (
    id VARCHAR(255) PRIMARY KEY,
    org_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL,
    modified_at BIGINT NOT NULL,
    FOREIGN KEY (org_id) REFERENCES organisations(id) ON DELETE CASCADE
);

-- Indexes for teams table
CREATE INDEX idx_teams_org_id ON teams(org_id);
CREATE INDEX idx_teams_name ON teams(name);
CREATE INDEX idx_teams_created_at ON teams(created_at);

-- Unique constraint for team name within an organisation
CREATE UNIQUE INDEX idx_teams_org_name_unique ON teams(org_id, name);

-- =====================================================
-- USER_AUTH TABLE (Sensitive Authentication Data)
-- =====================================================
CREATE TABLE user_auth (
    id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone_number VARCHAR(50),
    password_hash VARCHAR(255),
    auth_token TEXT,
    refresh_token TEXT,
    token_expires_at BIGINT,
    google_id VARCHAR(255),
    last_login_at BIGINT,
    password_reset_token VARCHAR(255),
    password_reset_expires_at BIGINT,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

-- Indexes for user_auth table
CREATE INDEX idx_user_auth_email ON user_auth(email);
CREATE INDEX idx_user_auth_google_id ON user_auth(google_id);
CREATE INDEX idx_user_auth_password_reset_token ON user_auth(password_reset_token);

-- =====================================================
-- USER_PROFILES TABLE (Non-Sensitive User Data)
-- =====================================================
CREATE TABLE user_profiles (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255),
    bio TEXT,
    profile_picture VARCHAR(500),
    company VARCHAR(255),
    role VARCHAR(50) NOT NULL DEFAULT 'user',
    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    org_ids JSONB DEFAULT '[]'::jsonb,
    team_ids JSONB DEFAULT '[]'::jsonb,
    user_updated_fields TEXT NOT NULL DEFAULT '[]',
    last_google_sync BIGINT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    FOREIGN KEY (id) REFERENCES user_auth(id) ON DELETE CASCADE
);

-- Indexes for user_profiles table
CREATE INDEX idx_user_profiles_name ON user_profiles(name);
CREATE INDEX idx_user_profiles_org_ids ON user_profiles USING GIN (org_ids);
CREATE INDEX idx_user_profiles_team_ids ON user_profiles USING GIN (team_ids);
CREATE INDEX idx_user_profiles_role ON user_profiles(role);
CREATE INDEX idx_user_profiles_created_at ON user_profiles(created_at);

-- =====================================================
-- RESOURCES TABLE
-- =====================================================
CREATE TABLE resources (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    config JSONB NOT NULL,
    group_name VARCHAR(255),
    namespace VARCHAR(255),
    created_at BIGINT NOT NULL,
    modified_at BIGINT NOT NULL
);

-- Indexes for resources table
CREATE INDEX idx_resources_type ON resources(type);
CREATE INDEX idx_resources_group ON resources(group_name);
CREATE INDEX idx_resources_namespace ON resources(namespace);
CREATE INDEX idx_resources_name ON resources(name);
CREATE UNIQUE INDEX idx_resources_unique ON resources(name, type, COALESCE(group_name, ''), COALESCE(namespace, ''));

-- =====================================================
-- FLOW VERSIONS TABLE
-- =====================================================
CREATE TABLE flow_versions (
    id VARCHAR(255) PRIMARY KEY,
    flow_id VARCHAR(255) NOT NULL,
    version INTEGER NOT NULL,
    steps JSONB NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE(flow_id, version)
);

-- Indexes for flow_versions table
CREATE INDEX idx_flow_versions_flow_id ON flow_versions(flow_id);
CREATE INDEX idx_flow_versions_version ON flow_versions(version);
CREATE INDEX idx_flow_versions_created_at ON flow_versions(created_at);

-- =====================================================
-- FLOWS TABLE
-- =====================================================
CREATE TABLE flows (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    creator VARCHAR(255) NOT NULL,
    steps JSONB NOT NULL,
    variables JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at BIGINT NOT NULL,
    modified_at BIGINT NOT NULL,
    flow_version INTEGER NOT NULL DEFAULT 1,
    org_id VARCHAR(255),
    team_id VARCHAR(255),
    FOREIGN KEY (org_id) REFERENCES organisations(id) ON DELETE SET NULL,
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE SET NULL
);

-- Indexes for flows table
CREATE INDEX idx_flows_name ON flows(name);
CREATE INDEX idx_flows_creator ON flows(creator);
CREATE INDEX idx_flows_created_at ON flows(created_at);
CREATE INDEX idx_flows_modified_at ON flows(modified_at);
CREATE INDEX idx_flows_org_id ON flows(org_id);
CREATE INDEX idx_flows_team_id ON flows(team_id);

-- =====================================================
-- EXECUTIONS TABLE
-- =====================================================
CREATE TABLE executions (
    id VARCHAR(255) PRIMARY KEY,
    flow_id VARCHAR(255) NOT NULL,
    flow_version INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    started_at BIGINT,
    completed_at BIGINT,
    steps JSONB NOT NULL,
    variables JSONB NOT NULL DEFAULT '[]'::jsonb,
    FOREIGN KEY (flow_id) REFERENCES flows(id) ON DELETE CASCADE
);

-- Indexes for executions table
CREATE INDEX idx_executions_flow_id ON executions(flow_id);
CREATE INDEX idx_executions_status ON executions(status);
CREATE INDEX idx_executions_started_at ON executions(started_at);
CREATE INDEX idx_executions_completed_at ON executions(completed_at);

-- =====================================================
-- TEST SUITES TABLE
-- =====================================================
CREATE TABLE test_suites (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    creator VARCHAR(255) NOT NULL,
    flows JSONB NOT NULL,
    run_unordered BOOLEAN NOT NULL DEFAULT FALSE,
    createdat BIGINT NOT NULL,
    modifiedat BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    org_id VARCHAR(255),
    team_id VARCHAR(255),
    FOREIGN KEY (org_id) REFERENCES organisations(id) ON DELETE SET NULL,
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE SET NULL
);

-- Indexes for test_suites table
CREATE INDEX idx_test_suites_name ON test_suites(name);
CREATE INDEX idx_test_suites_creator ON test_suites(creator);
CREATE INDEX idx_test_suites_enabled ON test_suites(enabled);
CREATE INDEX idx_test_suites_createdat ON test_suites(createdat);
CREATE INDEX idx_test_suites_modifiedat ON test_suites(modifiedat);
CREATE INDEX idx_test_suites_org_id ON test_suites(org_id);
CREATE INDEX idx_test_suites_team_id ON test_suites(team_id);

-- =====================================================
-- TEST SUITE EXECUTIONS TABLE
-- =====================================================
CREATE TABLE test_suite_executions (
    id VARCHAR(255) PRIMARY KEY,
    test_suite_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    started_at BIGINT,
    completed_at BIGINT,
    executions JSONB NOT NULL,
    FOREIGN KEY (test_suite_id) REFERENCES test_suites(id) ON DELETE CASCADE
);

-- Indexes for test_suite_executions table
CREATE INDEX idx_test_suite_executions_test_suite_id ON test_suite_executions(test_suite_id);
CREATE INDEX idx_test_suite_executions_status ON test_suite_executions(status);
CREATE INDEX idx_test_suite_executions_started_at ON test_suite_executions(started_at);
CREATE INDEX idx_test_suite_executions_completed_at ON test_suite_executions(completed_at);

-- =====================================================
-- SAMPLE DATA (OPTIONAL)
-- =====================================================

-- Sample organisation
INSERT INTO organisations (id, name, created_at, modified_at) VALUES 
('org-sample-1', 'Sample Organization', EXTRACT(EPOCH FROM NOW()), EXTRACT(EPOCH FROM NOW()));

-- Sample team
INSERT INTO teams (id, org_id, name, created_at, modified_at) VALUES 
('team-sample-1', 'org-sample-1', 'Backend Team', EXTRACT(EPOCH FROM NOW()), EXTRACT(EPOCH FROM NOW()));

-- Sample HTTP resource
INSERT INTO resources (id, name, type, config, group_name, namespace, created_at, modified_at) VALUES 
('resource-http-1', 'Sample API', 'http', '{"baseUrl": "https://api.example.com", "timeout": 5000}', 'apis', 'production', EXTRACT(EPOCH FROM NOW()), EXTRACT(EPOCH FROM NOW()));

-- Sample PostgreSQL resource
INSERT INTO resources (id, name, type, config, group_name, namespace, created_at, modified_at) VALUES 
('resource-db-1', 'Main Database', 'postgresql', '{"host": "localhost", "port": "5432", "database": "testdb", "username": "testuser", "password": "testpass"}', 'databases', 'production', EXTRACT(EPOCH FROM NOW()), EXTRACT(EPOCH FROM NOW()));

-- Sample Redis resource
INSERT INTO resources (id, name, type, config, group_name, namespace, created_at, modified_at) VALUES 
('resource-redis-1', 'Cache Server', 'redis', '{"host": "localhost", "port": "6379", "database": "0", "timeout": "2000"}', 'cache', 'production', EXTRACT(EPOCH FROM NOW()), EXTRACT(EPOCH FROM NOW()));

-- Sample Kafka resource
INSERT INTO resources (id, name, type, config, group_name, namespace, created_at, modified_at) VALUES 
('resource-kafka-1', 'Message Broker', 'kafka', '{"bootstrapServers": "localhost:9092", "securityProtocol": "PLAINTEXT"}', 'messaging', 'production', EXTRACT(EPOCH FROM NOW()), EXTRACT(EPOCH FROM NOW()));

-- =====================================================
-- COMMENTS AND DOCUMENTATION
-- =====================================================

-- This schema supports:
-- 1. Hierarchical organization structure (organisations -> teams)
-- 2. Multiple resource types (HTTP, PostgreSQL, Redis, Kafka)
-- 3. Flow versioning and execution tracking
-- 4. Test suite management with execution history
-- 5. Proper indexing for performance
-- 6. Foreign key constraints for data integrity
-- 7. Optional team/organization associations for flows and test suites

-- Resource types supported:
-- - http: HTTP/REST API endpoints
-- - postgresql: PostgreSQL database connections
-- - redis: Redis cache connections  
-- - kafka: Apache Kafka message brokers

-- Step types supported in flows:
-- - http: HTTP requests
-- - sql: SQL SELECT queries (PostgreSQL)
-- - redis: Redis cache operations
-- - kafka_publish: Kafka message publishing
-- - kafka_subscribe: Kafka message consumption
-- - delay: Time delays

-- All timestamps are stored as Unix epoch seconds for consistency
