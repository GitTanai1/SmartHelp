package com.smarthelp.repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.smarthelp.model.AgentApproval;

@Repository
public class AgentApprovalRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentApprovalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AgentApproval createOrFindResolution(UUID runId, Long ticketId, String message, String priority) {
        jdbcTemplate.update("""
                INSERT INTO agent_approvals
                    (id, run_id, ticket_id, action_type, proposed_message, proposed_priority, status)
                VALUES (?, ?, ?, 'TICKET_RESOLUTION', ?, ?, 'PENDING')
                ON DUPLICATE KEY UPDATE id = id
                """, UUID.randomUUID().toString(), runId.toString(), ticketId, message, priority);
        return findByRunAndAction(runId, "TICKET_RESOLUTION").orElseThrow();
    }

    public Optional<AgentApproval> findById(UUID id) {
        return jdbcTemplate.query("""
                SELECT id, run_id, ticket_id, action_type, proposed_message, proposed_priority, status,
                       requested_at, decided_at, decided_by, decision_note
                FROM agent_approvals WHERE id = ?
                """, (rs, row) -> map(rs.getString("id"), rs.getString("run_id"), rs.getLong("ticket_id"),
                rs.getString("action_type"), rs.getString("proposed_message"), rs.getString("proposed_priority"),
                rs.getString("status"), rs.getTimestamp("requested_at"), rs.getTimestamp("decided_at"),
                rs.getString("decided_by"), rs.getString("decision_note")), id.toString()).stream().findFirst();
    }

    public List<AgentApproval> findByRunId(UUID runId) {
        return jdbcTemplate.query("""
                SELECT id, run_id, ticket_id, action_type, proposed_message, proposed_priority, status,
                       requested_at, decided_at, decided_by, decision_note
                FROM agent_approvals WHERE run_id = ? ORDER BY requested_at ASC
                """, (rs, row) -> map(rs.getString("id"), rs.getString("run_id"), rs.getLong("ticket_id"),
                rs.getString("action_type"), rs.getString("proposed_message"), rs.getString("proposed_priority"),
                rs.getString("status"), rs.getTimestamp("requested_at"), rs.getTimestamp("decided_at"),
                rs.getString("decided_by"), rs.getString("decision_note")), runId.toString());
    }

    public boolean approve(UUID id, String actor, String note) {
        return jdbcTemplate.update("""
                UPDATE agent_approvals SET status = 'APPROVED', decided_at = CURRENT_TIMESTAMP,
                    decided_by = ?, decision_note = ? WHERE id = ? AND status = 'PENDING'
                """, actor, note, id.toString()) == 1;
    }

    public boolean reject(UUID id, String actor, String note) {
        return jdbcTemplate.update("""
                UPDATE agent_approvals SET status = 'REJECTED', decided_at = CURRENT_TIMESTAMP,
                    decided_by = ?, decision_note = ? WHERE id = ? AND status = 'PENDING'
                """, actor, note, id.toString()) == 1;
    }

    public void rejectPendingForRun(UUID runId, String actor, String note) {
        jdbcTemplate.update("""
                UPDATE agent_approvals SET status = 'REJECTED', decided_at = CURRENT_TIMESTAMP,
                    decided_by = ?, decision_note = ? WHERE run_id = ? AND status = 'PENDING'
                """, actor, note, runId.toString());
    }

    private Optional<AgentApproval> findByRunAndAction(UUID runId, String actionType) {
        return jdbcTemplate.query("""
                SELECT id, run_id, ticket_id, action_type, proposed_message, proposed_priority, status,
                       requested_at, decided_at, decided_by, decision_note
                FROM agent_approvals WHERE run_id = ? AND action_type = ?
                """, (rs, row) -> map(rs.getString("id"), rs.getString("run_id"), rs.getLong("ticket_id"),
                rs.getString("action_type"), rs.getString("proposed_message"), rs.getString("proposed_priority"),
                rs.getString("status"), rs.getTimestamp("requested_at"), rs.getTimestamp("decided_at"),
                rs.getString("decided_by"), rs.getString("decision_note")), runId.toString(), actionType).stream().findFirst();
    }

    private static AgentApproval map(String id, String runId, Long ticketId, String actionType, String message,
            String priority, String status, Timestamp requestedAt, Timestamp decidedAt, String decidedBy, String note) {
        return new AgentApproval(UUID.fromString(id), UUID.fromString(runId), ticketId, actionType, message, priority,
                status, requestedAt.toLocalDateTime(), decidedAt == null ? null : decidedAt.toLocalDateTime(), decidedBy, note);
    }
}
