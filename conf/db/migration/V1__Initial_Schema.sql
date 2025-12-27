-- PostgreSQL Schema for Async Testing Service
-- Generated to match Slick Repository definitions
-- Updated: 2025-12-27

-- Enable UUID extension for generating UUIDs
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =====================================================
-- ORGANISATIONS TABLE
-- Repository: OrganisationRepository.scala
-- =====================================================
CREATE TABLE organisations (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL,
    modified_at BIGINT NOT NULL
);

-- =====================================================
-- TEAMS TABLE
-- Repository: TeamRepository.scala
-- =====================================================
CREATE TABLE teams (
    id VARCHAR(255) PRIMARY KEY,
    org_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL,
    modified_at BIGINT NOT NULL
);

CREATE INDEX idx_teams_org_id ON teams(org_id);

-- =====================================================
-- USER AUTH TABLE
-- Repository: UserAuthRepository.scala
-- =====================================================
CREATE TABLE user_auth (
    id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone_number VARCHAR(255),
    password_hash VARCHAR(255),
    auth_token VARCHAR(255),
    refresh_token VARCHAR(255),
    token_expires_at BIGINT,
    google_id VARCHAR(255),
    last_login_at BIGINT,
    password_reset_token VARCHAR(255),
    password_reset_expires_at BIGINT,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_user_auth_email ON user_auth(email);
CREATE INDEX idx_user_auth_google_id ON user_auth(google_id);
CREATE INDEX idx_user_auth_auth_token ON user_auth(auth_token);

-- =====================================================
-- USER PROFILES TABLE
-- Repository: UserProfileRepository.scala
-- =====================================================
CREATE TABLE user_profiles (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255),
    bio TEXT,
    profile_picture TEXT,
    company VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    org_ids TEXT,  -- JSON List[String] stored as TEXT
    team_ids TEXT, -- JSON List[String] stored as TEXT
    user_updated_fields TEXT NOT NULL DEFAULT '[]', -- JSON Set[String] stored as TEXT
    last_google_sync BIGINT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_user_profiles_role ON user_profiles(role);

-- =====================================================
-- FLOWS TABLE
-- Repository: FlowRepository.scala
-- =====================================================
CREATE TABLE flows (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    creator VARCHAR(255) NOT NULL,
    steps TEXT NOT NULL, -- JSON stored as TEXT
    variables TEXT,      -- JSON stored as TEXT
    created_at BIGINT NOT NULL,
    modified_at BIGINT NOT NULL,
    flow_version INTEGER NOT NULL,
    org_id VARCHAR(255),
    team_id VARCHAR(255)
);

CREATE INDEX idx_flows_org_id ON flows(org_id);
CREATE INDEX idx_flows_team_id ON flows(team_id);
CREATE INDEX idx_flows_creator ON flows(creator);

-- =====================================================
-- FLOW VERSIONS TABLE
-- Repository: FlowVersionRepository.scala
-- =====================================================
CREATE TABLE flow_versions (
    id VARCHAR(255) PRIMARY KEY,
    flow_id VARCHAR(255) NOT NULL,
    version INTEGER NOT NULL,
    steps TEXT NOT NULL, -- JSON stored as TEXT
    created_at BIGINT NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    description TEXT,
    
    CONSTRAINT fk_flow_versions_flow_id FOREIGN KEY (flow_id) REFERENCES flows(id) ON DELETE CASCADE,
    CONSTRAINT flow_versions_unique_flow_version UNIQUE (flow_id, version)
);

CREATE INDEX idx_flow_versions_flow_id ON flow_versions(flow_id);

-- =====================================================
-- TEST SUITES TABLE
-- Repository: TestSuiteRepository.scala
-- =====================================================
CREATE TABLE test_suites (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    creator VARCHAR(255) NOT NULL,
    flows TEXT NOT NULL, -- JSON stored as TEXT
    rununordered BOOLEAN NOT NULL DEFAULT FALSE,
    createdat BIGINT NOT NULL,
    modifiedat BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    org_id VARCHAR(255),
    team_id VARCHAR(255)
);

CREATE INDEX idx_test_suites_org_id ON test_suites(org_id);
CREATE INDEX idx_test_suites_team_id ON test_suites(team_id);
CREATE INDEX idx_test_suites_creator ON test_suites(creator);

-- =====================================================
-- TEST SUITE EXECUTIONS TABLE
-- Repository: TestSuiteExecutionRepository.scala
-- =====================================================
CREATE TABLE test_suite_executions (
    id VARCHAR(255) PRIMARY KEY,
    testsuiteid VARCHAR(255) NOT NULL,
    testsuitename VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    startedat TIMESTAMP WITH TIME ZONE NOT NULL,
    completedat TIMESTAMP WITH TIME ZONE,
    flowexecutions TEXT NOT NULL, -- JSON stored as TEXT
    rununordered BOOLEAN NOT NULL DEFAULT FALSE,
    triggeredby VARCHAR(255) NOT NULL,
    
    CONSTRAINT fk_test_suite_executions_test_suite_id FOREIGN KEY (testsuiteid) REFERENCES test_suites(id) ON DELETE CASCADE
);

CREATE INDEX idx_test_suite_executions_testsuiteid ON test_suite_executions(testsuiteid);
CREATE INDEX idx_test_suite_executions_status ON test_suite_executions(status);

-- =====================================================
-- EXECUTIONS TABLE
-- Repository: ExecutionRepository.scala
-- =====================================================
CREATE TABLE executions (
    id VARCHAR(255) PRIMARY KEY,
    flowid VARCHAR(255) NOT NULL,
    flowversion INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    startedat TIMESTAMP WITH TIME ZONE NOT NULL,
    completedat TIMESTAMP WITH TIME ZONE,
    steps TEXT NOT NULL, -- JSON stored as TEXT
    variables TEXT,      -- JSON stored as TEXT
    updatedat TIMESTAMP WITH TIME ZONE NOT NULL,
    parameters TEXT,     -- JSON stored as TEXT
    testsuiteexecutionid VARCHAR(255),

    CONSTRAINT fk_executions_flow_id FOREIGN KEY (flowid) REFERENCES flows(id) ON DELETE CASCADE,
    CONSTRAINT fk_executions_test_suite_execution_id FOREIGN KEY (testsuiteexecutionid) REFERENCES test_suite_executions(id) ON DELETE SET NULL
);

CREATE INDEX idx_executions_flowid ON executions(flowid);
CREATE INDEX idx_executions_status ON executions(status);
CREATE INDEX idx_executions_testsuiteexecutionid ON executions(testsuiteexecutionid);

-- =====================================================
-- RESOURCE CONFIGS TABLE
-- Repository: ResourceRepository.scala
-- =====================================================
CREATE TABLE resource_configs (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    "group" VARCHAR(255),
    namespace VARCHAR(255),
    payload_json TEXT NOT NULL -- JSON stored as TEXT to support LIKE queries
);

CREATE INDEX idx_resource_configs_type ON resource_configs(type);
CREATE INDEX idx_resource_configs_group ON resource_configs("group");