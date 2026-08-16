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

import com.smarthelp.model.KnowledgeArticle;

@Repository
public class KnowledgeRepository {

    private static final RowMapper<KnowledgeArticle> KNOWLEDGE_ROW_MAPPER = (rs, rowNum) -> new KnowledgeArticle(
            rs.getLong("id"),
            rs.getLong("category_id"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime());

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public KnowledgeArticle create(Long categoryId, String title, String content) {
        String sql = "INSERT INTO knowledge_articles (category_id, title, content) VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, categoryId);
            ps.setString(2, title);
            ps.setString(3, content);
            return ps;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public List<KnowledgeArticle> findAll(Long categoryId, String query) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, category_id, title, content, created_at, updated_at
                FROM knowledge_articles
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (categoryId != null) {
            sql.append(" AND category_id = ?");
            params.add(categoryId);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (LOWER(title) LIKE ? OR LOWER(content) LIKE ?)");
            String like = "%" + query.toLowerCase() + "%";
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY updated_at DESC, id DESC");
        return jdbcTemplate.query(sql.toString(), KNOWLEDGE_ROW_MAPPER, params.toArray());
    }

    public Optional<KnowledgeArticle> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT id, category_id, title, content, created_at, updated_at FROM knowledge_articles WHERE id = ?",
                    KNOWLEDGE_ROW_MAPPER,
                    id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean update(Long id, Long categoryId, String title, String content) {
        int rows = jdbcTemplate.update(
                "UPDATE knowledge_articles SET category_id = ?, title = ?, content = ? WHERE id = ?",
                categoryId,
                title,
                content,
                id);
        return rows > 0;
    }

    public boolean delete(Long id) {
        int rows = jdbcTemplate.update("DELETE FROM knowledge_articles WHERE id = ?", id);
        return rows > 0;
    }
}
