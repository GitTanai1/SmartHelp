CREATE TABLE agent_runs (
  id CHAR(36) PRIMARY KEY,
  ticket_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  requested_by VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  error_message VARCHAR(1000) NULL,
  CONSTRAINT fk_agent_run_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE RESTRICT,
  CONSTRAINT chk_agent_run_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
  INDEX idx_agent_runs_ticket_created (ticket_id, created_at)
);

CREATE TABLE agent_run_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id CHAR(36) NOT NULL,
  node VARCHAR(80) NOT NULL,
  status VARCHAR(30) NOT NULL,
  payload TEXT NOT NULL,
  occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_agent_run_event_run FOREIGN KEY (run_id) REFERENCES agent_runs(id) ON DELETE CASCADE,
  INDEX idx_agent_run_events_run_id (run_id, id)
);
