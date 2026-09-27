-- Ticket lists commonly scope to a customer or status and order newest first.
-- These composites avoid an additional filesort after the selective predicate.
CREATE INDEX idx_tickets_user_created_id ON tickets (user_id, created_at DESC, id DESC);
CREATE INDEX idx_tickets_status_created_id ON tickets (status, created_at DESC, id DESC);
CREATE INDEX idx_tickets_category_created_id ON tickets (category_id, created_at DESC, id DESC);

-- Ticket detail loads responses in chronological order.
CREATE INDEX idx_ticket_responses_ticket_created_id ON ticket_responses (ticket_id, created_at ASC, id ASC);

-- Knowledge retrieval filters by category before ranking most recently updated articles.
CREATE INDEX idx_knowledge_category_updated_id ON knowledge_articles (category_id, updated_at DESC, id DESC);

-- Operator explorer lists approval requests by run in request order.
CREATE INDEX idx_agent_approvals_run_requested ON agent_approvals (run_id, requested_at ASC);
