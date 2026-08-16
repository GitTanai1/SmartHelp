package com.smarthelp.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.smarthelp.model.Category;

@Repository
public class CategoryRepository {

    private static final RowMapper<Category> CATEGORY_ROW_MAPPER = (rs, rowNum) -> new Category(
            rs.getLong("id"),
            rs.getString("name"));

    private final JdbcTemplate jdbcTemplate;

    public CategoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Category create(String name) {
        String sql = "INSERT INTO categories (name) VALUES (?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            return ps;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public List<Category> findAll() {
        return jdbcTemplate.query("SELECT id, name FROM categories ORDER BY name", CATEGORY_ROW_MAPPER);
    }

    public Optional<Category> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT id, name FROM categories WHERE id = ?",
                    CATEGORY_ROW_MAPPER,
                    id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<Category> findByName(String name) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT id, name FROM categories WHERE name = ?",
                    CATEGORY_ROW_MAPPER,
                    name));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean existsById(Long id) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public boolean update(Long id, String name) {
        int rows = jdbcTemplate.update("UPDATE categories SET name = ? WHERE id = ?", name, id);
        return rows > 0;
    }

    public boolean delete(Long id) {
        int rows = jdbcTemplate.update("DELETE FROM categories WHERE id = ?", id);
        return rows > 0;
    }
}
