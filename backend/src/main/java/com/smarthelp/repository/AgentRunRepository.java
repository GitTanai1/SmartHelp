package com.smarthelp.repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.smarthelp.model.AgentRun;
import com.smarthelp.model.AgentRunEvent;

@Repository
public class AgentRunRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AgentRun create(UUID id, Long ticketId, String requestedBy, String requestId) {
        jdbcTemplate.update("""
                INSERT INTO agent_runs (id, ticket_id, status, requested_by, request_id)
                VALUES (?, ?, 'RUNNING', ?, ?)
                """, id.toString(), ticketId, requestedBy, requestId);
        return findById(id).orElseThrow();
    }

    public Optional<AgentRun> findById(UUID id) {
        return jdbcTemplate.query("""
                SELECT id, ticket_id, status, requested_by, request_id, created_at, updated_at, completed_at, error_message
                FROM agent_runs WHERE id = ?
                """, (rs, row) -> new AgentRun(
                UUID.fromString(rs.getString("id")), rs.getLong("ticket_id"), rs.getString("status"),
                rs.getString("requested_by"), rs.getString("request_id"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime(), nullableTime(rs.getTimestamp("completed_at")),
                rs.getString("error_message")), id.toString()).stream().findFirst();
    }

    public List<AgentRun> findByTicketId(Long ticketId) {
        return jdbcTemplate.query("""
                SELECT id, ticket_id, status, requested_by, request_id, created_at, updated_at, completed_at, error_message
                FROM agent_runs WHERE ticket_id = ? ORDER BY created_at DESC
                """, (rs, row) -> new AgentRun(
                UUID.fromString(rs.getString("id")), rs.getLong("ticket_id"), rs.getString("status"),
                rs.getString("requested_by"), rs.getString("request_id"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime(), nullableTime(rs.getTimestamp("completed_at")),
                rs.getString("error_message")), ticketId);
    }

    public void complete(UUID id) {
        jdbcTemplate.update("""
                UPDATE agent_runs SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'RUNNING'
                """, id.toString());
    }

    public void fail(UUID id, String message) {
        jdbcTemplate.update("""
                UPDATE agent_runs SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP, error_message = ?
                WHERE id = ? AND status = 'RUNNING'
                """, message.substring(0, Math.min(message.length(), 1000)), id.toString());
    }

    public boolean cancel(UUID id) {
        return jdbcTemplate.update("""
                UPDATE agent_runs SET status = 'CANCELLED', completed_at = CURRENT_TIMESTAMP,
                    error_message = 'Cancelled by an operator'
                WHERE id = ? AND status IN ('RUNNING', 'WAITING_FOR_APPROVAL')
                """, id.toString()) == 1;
    }

    public boolean waitForApproval(UUID id) {
        return jdbcTemplate.update("""
                UPDATE agent_runs SET status = 'WAITING_FOR_APPROVAL'
                WHERE id = ? AND status = 'RUNNING'
                """, id.toString()) == 1;
    }

    public boolean resumeFromApproval(UUID id) {
        return jdbcTemplate.update("""
                UPDATE agent_runs SET status = 'RUNNING'
                WHERE id = ? AND status = 'WAITING_FOR_APPROVAL'
                """, id.toString()) == 1;
    }

    public boolean resumeInterrupted(UUID id) {
        return jdbcTemplate.update("""
                UPDATE agent_runs SET status = 'RUNNING', completed_at = NULL, error_message = NULL
                WHERE id = ? AND status IN ('RUNNING', 'FAILED')
                """, id.toString()) == 1;
    }

    public boolean isRunning(UUID id, Long ticketId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM agent_runs
                WHERE id = ? AND ticket_id = ? AND status = 'RUNNING'
                """, Integer.class, id.toString(), ticketId);
        return count != null && count == 1;
    }

    public boolean isWaitingForApproval(UUID id, Long ticketId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM agent_runs
                WHERE id = ? AND ticket_id = ? AND status = 'WAITING_FOR_APPROVAL'
                """, Integer.class, id.toString(), ticketId);
        return count != null && count == 1;
    }

    public void appendEvent(UUID runId, String node, String status, String payload) {
        jdbcTemplate.update("""
                INSERT INTO agent_run_events (run_id, node, status, payload)
                VALUES (?, ?, ?, ?)
                """, runId.toString(), node, status, payload);
    }

    public List<AgentRunEvent> findEvents(UUID runId) {
        return jdbcTemplate.query("""
                SELECT id, run_id, node, status, payload, occurred_at
                FROM agent_run_events WHERE run_id = ? ORDER BY id ASC
                """, (rs, row) -> new AgentRunEvent(
                rs.getLong("id"), UUID.fromString(rs.getString("run_id")), rs.getString("node"),
                rs.getString("status"), rs.getString("payload"), rs.getTimestamp("occurred_at").toLocalDateTime()),
                runId.toString());
    }

    public List<AgentRunEvent> findEventsAfter(UUID runId, long afterEventId) {
        return jdbcTemplate.query("""
                SELECT id, run_id, node, status, payload, occurred_at
                FROM agent_run_events WHERE run_id = ? AND id > ? ORDER BY id ASC
                """, (rs, row) -> new AgentRunEvent(
                rs.getLong("id"), UUID.fromString(rs.getString("run_id")), rs.getString("node"),
                rs.getString("status"), rs.getString("payload"), rs.getTimestamp("occurred_at").toLocalDateTime()),
                runId.toString(), afterEventId);
    }

    private static java.time.LocalDateTime nullableTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
