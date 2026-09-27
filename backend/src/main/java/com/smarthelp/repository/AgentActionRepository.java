package com.smarthelp.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AgentActionRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentActionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @return true only when this transaction claimed a new action key. */
    public boolean tryStart(String key, String actionType, String requestHash, Long ticketId) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO agent_action_executions
                        (idempotency_key, action_type, request_hash, status, ticket_id)
                    VALUES (?, ?, ?, 'IN_PROGRESS', ?)
                    """, key, actionType, requestHash, ticketId);
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    public Optional<ActionExecution> findByKey(String key) {
        return jdbcTemplate.query("""
                SELECT idempotency_key, action_type, request_hash, status, ticket_id, created_at, completed_at
                FROM agent_action_executions WHERE idempotency_key = ?
                """, (rs, row) -> new ActionExecution(
                rs.getString("idempotency_key"), rs.getString("action_type"), rs.getString("request_hash"),
                rs.getString("status"), rs.getLong("ticket_id"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime()), key)
                .stream().findFirst();
    }

    public void markCompleted(String key) {
        jdbcTemplate.update("""
                UPDATE agent_action_executions
                SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP
                WHERE idempotency_key = ?
                """, key);
    }

    public record ActionExecution(
            String idempotencyKey,
            String actionType,
            String requestHash,
            String status,
            Long ticketId,
            LocalDateTime createdAt,
            LocalDateTime completedAt) {
    }
}
