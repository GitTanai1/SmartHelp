package com.smarthelp.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.smarthelp.dto.TicketDtos.TicketSummary;
import com.smarthelp.model.Ticket;

@Repository
public class TicketRepository {

    private static final RowMapper<Ticket> TICKET_ROW_MAPPER = (rs, rowNum) -> new Ticket(
            rs.getLong("id"),
            rs.getLong("user_id"),
            getNullableLong(rs, "category_id"),
            rs.getString("subject"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getString("priority"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime());

    private static final RowMapper<TicketSummary> SUMMARY_ROW_MAPPER = (rs, rowNum) -> new TicketSummary(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("user_name"),
            getNullableLong(rs, "category_id"),
            rs.getString("category_name"),
            rs.getString("subject"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getString("priority"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime());

    private final JdbcTemplate jdbcTemplate;

    public TicketRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Ticket create(Long userId, Long categoryId, String subject, String description, String priority) {
        String sql = """
                INSERT INTO tickets (user_id, category_id, subject, description, status, priority)
                VALUES (?, ?, ?, ?, 'OPEN', ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            if (categoryId == null) {
                ps.setObject(2, null);
            } else {
                ps.setLong(2, categoryId);
            }
            ps.setString(3, subject);
            ps.setString(4, description);
            ps.setString(5, priority);
            return ps;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public List<TicketSummary> findAll(String status, Long categoryId, Long userId, String priority) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.id, t.user_id, u.name AS user_name, t.category_id, c.name AS category_name,
                       t.subject, t.description, t.status, t.priority, t.created_at, t.updated_at
                FROM tickets t
                JOIN users u ON u.id = t.user_id
                LEFT JOIN categories c ON c.id = t.category_id
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND t.status = ?");
            params.add(status);
        }
        if (categoryId != null) {
            sql.append(" AND t.category_id = ?");
            params.add(categoryId);
        }
        if (userId != null) {
            sql.append(" AND t.user_id = ?");
            params.add(userId);
        }
        if (priority != null && !priority.isBlank()) {
            sql.append(" AND t.priority = ?");
            params.add(priority);
        }
        sql.append(" ORDER BY t.created_at DESC, t.id DESC");
        return jdbcTemplate.query(sql.toString(), SUMMARY_ROW_MAPPER, params.toArray());
    }

    public Optional<Ticket> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT id, user_id, category_id, subject, description, status, priority, created_at, updated_at FROM tickets WHERE id = ?",
                    TICKET_ROW_MAPPER,
                    id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<TicketSummary> findSummaryById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT t.id, t.user_id, u.name AS user_name, t.category_id, c.name AS category_name,
                           t.subject, t.description, t.status, t.priority, t.created_at, t.updated_at
                    FROM tickets t
                    JOIN users u ON u.id = t.user_id
                    LEFT JOIN categories c ON c.id = t.category_id
                    WHERE t.id = ?
                    """, SUMMARY_ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean existsById(Long id) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tickets WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public boolean update(Long id, Long categoryId, String subject, String description, String status, String priority) {
        int rows = jdbcTemplate.update("""
                UPDATE tickets
                SET category_id = ?, subject = ?, description = ?, status = ?, priority = ?
                WHERE id = ?
                """, categoryId, subject, description, status, priority, id);
        return rows > 0;
    }

    public boolean updateStatus(Long id, String status) {
        int rows = jdbcTemplate.update("UPDATE tickets SET status = ? WHERE id = ?", status, id);
        return rows > 0;
    }

    public boolean delete(Long id) {
        int rows = jdbcTemplate.update("DELETE FROM tickets WHERE id = ?", id);
        return rows > 0;
    }

    private static Long getNullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
