package com.smarthelp.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record AgentRun(
        UUID id,
        Long ticketId,
        String status,
        String requestedBy,
        String requestId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        String errorMessage) {
}
