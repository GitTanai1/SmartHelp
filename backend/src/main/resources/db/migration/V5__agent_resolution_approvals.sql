ALTER TABLE agent_runs DROP CHECK chk_agent_run_status;
ALTER TABLE agent_runs ADD CONSTRAINT chk_agent_run_status
    CHECK (status IN ('RUNNING', 'WAITING_FOR_APPROVAL', 'COMPLETED', 'FAILED', 'CANCELLED'));

CREATE TABLE agent_approvals (
  id CHAR(36) PRIMARY KEY,
  run_id CHAR(36) NOT NULL,
  ticket_id BIGINT NOT NULL,
  action_type VARCHAR(80) NOT NULL,
  proposed_message TEXT NOT NULL,
  proposed_priority VARCHAR(20) NULL,
  status VARCHAR(30) NOT NULL,
  requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  decided_at TIMESTAMP NULL,
  decided_by VARCHAR(255) NULL,
  decision_note VARCHAR(1000) NULL,
  CONSTRAINT fk_agent_approval_run FOREIGN KEY (run_id) REFERENCES agent_runs(id) ON DELETE RESTRICT,
  CONSTRAINT fk_agent_approval_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE RESTRICT,
  CONSTRAINT chk_agent_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  CONSTRAINT uq_agent_approval_run_action UNIQUE (run_id, action_type),
  INDEX idx_agent_approvals_ticket_status (ticket_id, status, requested_at)
);
