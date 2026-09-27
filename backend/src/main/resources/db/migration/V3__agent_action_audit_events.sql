CREATE TABLE audit_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_type VARCHAR(64) NOT NULL,
  actor_id VARCHAR(255) NOT NULL,
  ticket_id BIGINT NOT NULL,
  agent_action_key VARCHAR(128) NULL,
  request_id VARCHAR(36) NULL,
  details VARCHAR(1000) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_audit_event_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE RESTRICT,
  INDEX idx_audit_event_ticket_id (ticket_id),
  INDEX idx_audit_event_created_at (created_at),
  INDEX idx_audit_event_action_key (agent_action_key)
);
