CREATE TABLE agent_action_executions (
  idempotency_key VARCHAR(128) PRIMARY KEY,
  action_type VARCHAR(64) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL,
  ticket_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  CONSTRAINT fk_agent_action_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE RESTRICT,
  CONSTRAINT chk_agent_action_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
  INDEX idx_agent_action_ticket_id (ticket_id),
  INDEX idx_agent_action_created_at (created_at)
);
