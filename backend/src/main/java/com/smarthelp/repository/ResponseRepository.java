package com.smarthelp.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.smarthelp.model.TicketResponse;

@Repository
public class ResponseRepository {

    private static final RowMapper<TicketResponse> RESPONSE_ROW_MAPPER = (rs, rowNum) -> new TicketResponse(
            rs.getLong("id"),
            rs.getLong("ticket_id"),
            rs.getString("message"),
            rs.getString("sender_type"),
            rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbcTemplate;

    public ResponseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TicketResponse create(Long ticketId, String message, String senderType) {
        String sql = "INSERT INTO ticket_responses (ticket_id, message, sender_type) VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, ticketId);
            ps.setString(2, message);
            ps.setString(3, senderType);
            return ps;
        }, keyHolder);
        return findByTicketId(ticketId).stream()
                .filter(response -> response.id().equals(keyHolder.getKey().longValue()))
                .findFirst()
                .orElseThrow();
    }

    public List<TicketResponse> findByTicketId(Long ticketId) {
        return jdbcTemplate.query("""
                SELECT id, ticket_id, message, sender_type, created_at
                FROM ticket_responses
                WHERE ticket_id = ?
                ORDER BY created_at ASC, id ASC
                """, RESPONSE_ROW_MAPPER, ticketId);
    }
}
