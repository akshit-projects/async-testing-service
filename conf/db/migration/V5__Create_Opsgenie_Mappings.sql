CREATE TABLE alert_flow_mappings (
    id VARCHAR(255) PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    trigger_meta TEXT NOT NULL,
    flow_id VARCHAR(255) NOT NULL,
    reporting_config TEXT,
    org_id VARCHAR(255),
    team_id VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_alert_mappings_trigger ON alert_flow_mappings(provider);
