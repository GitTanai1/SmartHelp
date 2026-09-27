package com.smarthelp.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Audit events are append-only: this repository intentionally exposes no update/delete methods. */
    public void append(
            String eventType,
            String actorId,
            Long ticketId,
            String agentActionKey,
            String requestId,
            String details) {
        jdbcTemplate.update("""
                INSERT INTO audit_events
                    (event_type, actor_id, ticket_id, agent_action_key, request_id, details)
                VALUES (?, ?, ?, ?, ?, ?)
                """, eventType, actorId, ticketId, agentActionKey, requestId, details);
    }
}
