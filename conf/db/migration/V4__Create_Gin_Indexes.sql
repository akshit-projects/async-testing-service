CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_flows_name_trgm
ON flows USING gin (name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_flows_creator_trgm
ON flows USING gin (creator gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_test_suites_name_trgm
ON test_suites USING gin (name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_test_suites_creator_trgm
ON test_suites USING gin (creator gin_trgm_ops);