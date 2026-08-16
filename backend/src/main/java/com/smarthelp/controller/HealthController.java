package com.smarthelp.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HealthController exposes a simple liveness endpoint.
 *
 * GET /api/health
 *   Returns 200 with status=UP when the backend is running.
 *   Also reports whether the MySQL database connection is reachable.
 *
 * This is useful for:
 * - verifying that Spring Boot started,
 * - quick smoke tests after deployment,
 * - demonstrating the @RestController and Map return-type pattern.
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "smarthelp-backend");
        response.put("database", databaseStatus());
        return response;
    }

    private String databaseStatus() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception ex) {
            return "DOWN";
        }
    }
}
