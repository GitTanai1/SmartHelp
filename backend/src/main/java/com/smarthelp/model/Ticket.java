package com.smarthelp.model;

import java.time.LocalDateTime;

public record Ticket(
        Long id,
        Long userId,
        Long categoryId,
        String subject,
        String description,
        String status,
        String priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
