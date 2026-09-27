ALTER TABLE agent_runs ADD COLUMN request_id VARCHAR(36) NULL;
CREATE INDEX idx_agent_runs_request_id ON agent_runs (request_id);
