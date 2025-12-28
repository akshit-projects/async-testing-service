-- Drop the foreign key constraint that links executions to test_suite_executions
-- This allows executions to exist even if the suite execution is deleted or if they are created independently
ALTER TABLE executions DROP CONSTRAINT IF EXISTS fk_executions_test_suite_execution_id;
